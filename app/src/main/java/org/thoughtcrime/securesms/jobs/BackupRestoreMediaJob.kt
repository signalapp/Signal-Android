/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.RestorableAttachment
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import kotlin.time.Duration.Companion.days

/**
 * Job that is responsible for enqueueing attachment download
 * jobs upon restore.
 */
class BackupRestoreMediaJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupRestoreMediaJob::class.java)

    const val KEY = "BackupRestoreMediaJob"
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

  override fun onRun() {
    if (!SignalStore.account.isRegistered) {
      Log.e(TAG, "Not registered, cannot restore!")
      throw NotPushRegisteredException()
    }

    val jobManager = AppDependencies.jobManager
    val batchSize = 500
    val restoreTime = System.currentTimeMillis()

    do {
      val restoreThumbnailJobs: MutableList<RestoreAttachmentThumbnailJob> = mutableListOf()
      val restoreFullAttachmentJobs: MutableMap<RestorableAttachment, RestoreAttachmentJob> = mutableMapOf()

      val restoreThumbnailOnlyAttachments: MutableList<RestorableAttachment> = mutableListOf()
      val notRestorable: MutableList<RestorableAttachment> = mutableListOf()

      val attachmentBatch = SignalDatabase.attachments.getRestorableAttachments(batchSize)
      val messageIds = attachmentBatch.map { it.mmsId }.toSet()
      val messageMap = SignalDatabase.messages.getMessages(messageIds).associate { it.id to (it as MmsMessageRecord) }

      for (attachment in attachmentBatch) {
        val message = messageMap[attachment.mmsId]
        if (message == null) {
          Log.w(TAG, "Unable to find message for ${attachment.attachmentId}")
          notRestorable += attachment
          continue
        }

        restoreThumbnailJobs += RestoreAttachmentThumbnailJob(
          messageId = attachment.mmsId,
          attachmentId = attachment.attachmentId,
          highPriority = false
        )

        if (shouldRestoreFullSize(message, restoreTime, SignalStore.backup.optimizeStorage)) {
          restoreFullAttachmentJobs += attachment to RestoreAttachmentJob(
            messageId = attachment.mmsId,
            attachmentId = attachment.attachmentId
          )
        } else {
          restoreThumbnailOnlyAttachments += attachment
        }
      }

      SignalDatabase.rawDatabase.withinTransaction {
        // Mark not restorable thumbnails and attachments as failed
        SignalDatabase.attachments.setThumbnailRestoreState(notRestorable.map { it.attachmentId }, AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE)
        SignalDatabase.attachments.setRestoreTransferState(notRestorable, AttachmentTable.TRANSFER_PROGRESS_FAILED)

        // Mark restorable thumbnails and attachments as in progress
        SignalDatabase.attachments.setThumbnailRestoreState(restoreThumbnailJobs.map { it.attachmentId }, AttachmentTable.ThumbnailRestoreState.IN_PROGRESS)
        SignalDatabase.attachments.setRestoreTransferState(restoreFullAttachmentJobs.keys, AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS)

        // Set thumbnail only attachments as offloaded
        SignalDatabase.attachments.setRestoreTransferState(restoreThumbnailOnlyAttachments, AttachmentTable.TRANSFER_RESTORE_OFFLOADED)

        jobManager.addAll(restoreThumbnailJobs + restoreFullAttachmentJobs.values)
      }
    } while (restoreThumbnailJobs.isNotEmpty() && restoreFullAttachmentJobs.isNotEmpty() && notRestorable.isNotEmpty())

    SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()

    jobManager.add(CheckRestoreMediaLeftJob(RestoreAttachmentJob.constructQueueString()))
  }

  private fun shouldRestoreFullSize(message: MmsMessageRecord, restoreTime: Long, optimizeStorage: Boolean): Boolean {
    return !optimizeStorage || ((restoreTime - message.dateSent) < 30.days.inWholeMilliseconds)
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupRestoreMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupRestoreMediaJob {
      return BackupRestoreMediaJob(parameters)
    }
  }
}
