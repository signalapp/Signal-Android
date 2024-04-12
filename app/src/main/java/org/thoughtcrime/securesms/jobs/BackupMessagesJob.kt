/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.database.Cursor
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.providers.BlobProvider
import org.whispersystems.signalservice.api.NetworkResult
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Job that is responsible for exporting the DB as a backup proto and
 * also uploading the resulting proto.
 */
class BackupMessagesJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupMessagesJob::class.java)

    const val KEY = "BackupMessagesJob"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setMaxInstancesForFactory(2)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  private fun archiveAttachments() {
    if (BuildConfig.MESSAGE_BACKUP_RESTORE_ENABLED) {
      SignalStore.backup().canReadWriteToArchiveCdn = true
    }
    val batchSize = 100
    SignalDatabase.attachments.getArchivableAttachments().use { cursor ->
      while (!cursor.isAfterLast) {
        val attachments = cursor.readAttachmentBatch(batchSize)

        when (val archiveResult = BackupRepository.archiveMedia(attachments)) {
          is NetworkResult.Success -> {
            for (success in archiveResult.result.sourceNotFoundResponses) {
              val attachmentId = archiveResult.result.mediaIdToAttachmentId(success.mediaId)
              ApplicationDependencies
                .getJobManager()
                .startChain(AttachmentUploadJob(attachmentId))
                .then(ArchiveAttachmentJob(attachmentId))
                .enqueue()
            }
          }

          else -> {
            Log.e(TAG, "Failed to archive $archiveResult")
          }
        }
      }
    }
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
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(ApplicationDependencies.getApplication())

    val outputStream = FileOutputStream(tempBackupFile)
    BackupRepository.export(outputStream = outputStream, append = { tempBackupFile.appendBytes(it) }, plaintext = false)

    FileInputStream(tempBackupFile).use {
      BackupRepository.uploadBackupFile(it, tempBackupFile.length())
    }

    archiveAttachments()
    if (!tempBackupFile.delete()) {
      Log.e(TAG, "Failed to delete temp backup file")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupMessagesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMessagesJob {
      return BackupMessagesJob(parameters)
    }
  }
}
