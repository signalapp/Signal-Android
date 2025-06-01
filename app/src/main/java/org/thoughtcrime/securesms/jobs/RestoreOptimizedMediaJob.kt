/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Restores any media that was previously optimized and off-loaded into the user's archive. Leverages
 * the same archive restore progress/flow.
 */
class RestoreOptimizedMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(RestoreOptimizedMediaJob::class)
    const val KEY = "RestoreOptimizeMediaJob"

    fun enqueue() {
      val job = RestoreOptimizedMediaJob()
      AppDependencies.jobManager.add(job)
    }

    @JvmStatic
    fun enqueueIfNecessary() {
      if (SignalStore.backup.backsUpMedia && !SignalStore.backup.optimizeStorage) {
        AppDependencies.jobManager.add(RestoreOptimizedMediaJob())
      }
    }
  }

  private constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("RestoreOptimizeMediaJob")
      .setMaxInstancesForQueue(2)
      .setMaxAttempts(3)
      .build()
  )

  override fun run(): Result {
    if (SignalStore.backup.optimizeStorage && !SignalStore.backup.userManuallySkippedMediaRestore) {
      Log.i(TAG, "User is optimizing media and has not skipped restore, skipping.")
      return Result.success()
    }

    if (!SignalStore.backup.optimizeStorage && SignalStore.backup.userManuallySkippedMediaRestore) {
      Log.i(TAG, "User is not optimizing media but elected to skip media restore, skipping.")
      return Result.success()
    }

    val restorableAttachments = SignalDatabase.attachments.getRestorableOptimizedAttachments()

    if (restorableAttachments.isEmpty()) {
      return Result.success()
    }

    val jobManager = AppDependencies.jobManager

    restorableAttachments
      .forEach {
        val job = RestoreAttachmentJob.forOffloadedRestore(
          messageId = it.mmsId,
          attachmentId = it.attachmentId
        )

        // Intentionally enqueues one at a time for safer attachment transfer state management
        jobManager.add(job)
      }

    SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()

    AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.RESTORE_OFFLOADED)))

    return Result.success()
  }

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  class Factory : Job.Factory<RestoreOptimizedMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreOptimizedMediaJob {
      return RestoreOptimizedMediaJob(parameters)
    }
  }
}
