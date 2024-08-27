/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.database.Cursor
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.providers.BlobProvider
import org.whispersystems.signalservice.api.NetworkResult
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Job that is responsible for exporting the DB as a backup proto and
 * also uploading the resulting proto.
 */
class BackupMessagesJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupMessagesJob::class.java)

    const val KEY = "BackupMessagesJob"

    const val QUEUE = "BackupMessagesQueue"

    /**
     * Pruning abandoned remote media is relatively expensive, so we should
     * not do this every time we backup.
     */
    fun enqueue(pruneAbandonedRemoteMedia: Boolean = false) {
      val jobManager = AppDependencies.jobManager
      if (pruneAbandonedRemoteMedia) {
        jobManager
          .startChain(BackupMessagesJob())
          .then(SyncArchivedMediaJob())
          .enqueue()
      } else {
        jobManager.add(BackupMessagesJob())
      }
    }
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(if (SignalStore.backup.backupWithCellular) NetworkConstraint.KEY else WifiConstraint.KEY)
      .setMaxAttempts(3)
      .setMaxInstancesForFactory(1)
      .setQueue(QUEUE)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  private fun archiveAttachments(): Boolean {
    if (!SignalStore.backup.backsUpMedia) return false

    val batchSize = 100
    var needToBackfill = 0
    var totalCount: Int
    var progress = 0
    SignalDatabase.attachments.getArchivableAttachments().use { cursor ->
      totalCount = cursor.count
      while (!cursor.isAfterLast) {
        val attachments = cursor.readAttachmentBatch(batchSize)

        when (val archiveResult = BackupRepository.archiveMedia(attachments)) {
          is NetworkResult.Success -> {
            Log.i(TAG, "Archive call successful")
            for (notFound in archiveResult.result.sourceNotFoundResponses) {
              val attachmentId = archiveResult.result.mediaIdToAttachmentId(notFound.mediaId)
              Log.i(TAG, "Attachment $attachmentId not found on cdn, will need to re-upload")
              needToBackfill++
            }
            for (success in archiveResult.result.successfulResponses) {
              val attachmentId = archiveResult.result.mediaIdToAttachmentId(success.mediaId)
              ArchiveThumbnailUploadJob.enqueueIfNecessary(attachmentId)
            }
            progress += attachments.size
          }

          else -> {
            Log.e(TAG, "Failed to archive $archiveResult")
          }
        }
        EventBus.getDefault().postSticky(BackupV2Event(BackupV2Event.Type.PROGRESS_ATTACHMENTS, (progress - needToBackfill).toLong(), totalCount.toLong()))
      }
    }
    if (needToBackfill > 0) {
      AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob(totalCount = totalCount, progress = progress - needToBackfill))
      return true
    }
    return false
  }

  private fun Cursor.readAttachmentBatch(batchSize: Int): List<DatabaseAttachment> {
    val attachments = ArrayList<DatabaseAttachment>()
    for (i in 0 until batchSize) {
      if (this.moveToNext()) {
        attachments.addAll(SignalDatabase.attachments.getAttachments(this))
      } else {
        break
      }
    }
    return attachments
  }

  override fun onRun() {
    EventBus.getDefault().postSticky(BackupV2Event(type = BackupV2Event.Type.PROGRESS_MESSAGES, count = 0, estimatedTotalCount = 0))
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

    val outputStream = FileOutputStream(tempBackupFile)
    BackupRepository.export(outputStream = outputStream, append = { tempBackupFile.appendBytes(it) }, plaintext = false)

    FileInputStream(tempBackupFile).use {
      BackupRepository.uploadBackupFile(it, tempBackupFile.length())
    }
    val needBackfill = archiveAttachments()
    SignalStore.backup.lastBackupProtoSize = tempBackupFile.length()
    if (!tempBackupFile.delete()) {
      Log.e(TAG, "Failed to delete temp backup file")
    }
    SignalStore.backup.lastBackupTime = System.currentTimeMillis()
    if (!needBackfill) {
      EventBus.getDefault().postSticky(BackupV2Event(BackupV2Event.Type.FINISHED, 0, 0))
      try {
        SignalStore.backup.usedBackupMediaSpace = (BackupRepository.getRemoteBackupUsedSpace().successOrThrow() ?: 0)
      } catch (e: IOException) {
        Log.e(TAG, "Failed to update used space")
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupMessagesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMessagesJob {
      return BackupMessagesJob(parameters)
    }
  }
}
