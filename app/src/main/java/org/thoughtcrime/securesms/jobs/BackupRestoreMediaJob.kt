/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
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
    if (!SignalStore.account().isRegistered) {
      Log.e(TAG, "Not registered, cannot restore!")
      throw NotPushRegisteredException()
    }

    val jobManager = ApplicationDependencies.getJobManager()
    val batchSize = 100
    val restoreTime = System.currentTimeMillis()
    var restoreJobBatch: List<RestoreAttachmentJob>
    do {
      val attachmentBatch = SignalDatabase.attachments.getRestorableAttachments(batchSize)
      val messageIds = attachmentBatch.map { it.mmsId }.toSet()
      val messageMap = SignalDatabase.messages.getMessages(messageIds).associate { it.id to (it as MmsMessageRecord) }
      restoreJobBatch = SignalDatabase.attachments.getRestorableAttachments(batchSize).map { attachment ->
        val message = messageMap[attachment.mmsId]!!
        RestoreAttachmentJob(
          messageId = attachment.mmsId,
          attachmentId = attachment.attachmentId,
          manual = false,
          forceArchiveDownload = true,
          fullSize = shouldRestoreFullSize(message, restoreTime, optimizeStorage = SignalStore.backup().optimizeStorage)
        )
      }
      jobManager.addAll(restoreJobBatch)
    } while (restoreJobBatch.isNotEmpty())
  }

  private fun shouldRestoreFullSize(message: MmsMessageRecord, restoreTime: Long, optimizeStorage: Boolean): Boolean {
    return ((restoreTime - message.dateSent) < 30.days.inWholeMilliseconds) || !optimizeStorage
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupRestoreMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupRestoreMediaJob {
      return BackupRestoreMediaJob(parameters)
    }
  }
}
