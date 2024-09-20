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
import kotlin.time.Duration.Companion.days

/**
 * Optimizes media storage by relying on backups for full copies of files and only keeping thumbnails locally.
 */
class OptimizeMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(OptimizeMediaJob::class)
    const val KEY = "OptimizeMediaJob"

    fun enqueue() {
      if (!SignalStore.backup.optimizeStorage || !SignalStore.backup.backsUpMedia) {
        Log.i(TAG, "Optimize media is not enabled, skipping. backsUpMedia: ${SignalStore.backup.backsUpMedia} optimizeStorage: ${SignalStore.backup.optimizeStorage}")
        return
      }

      AppDependencies.jobManager.add(OptimizeMediaJob())
    }
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("OptimizeMediaJob")
      .setMaxInstancesForQueue(2)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(3)
      .build()
  )

  override fun run(): Result {
    if (!SignalStore.backup.optimizeStorage || !SignalStore.backup.backsUpMedia) {
      Log.i(TAG, "Optimize media is not enabled, aborting. backsUpMedia: ${SignalStore.backup.backsUpMedia} optimizeStorage: ${SignalStore.backup.optimizeStorage}")
      return Result.success()
    }

    Log.i(TAG, "Canceling any previous restore optimized media jobs and cleanup progress")
    AppDependencies.jobManager.cancelAllInQueue(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.RESTORE_OFFLOADED))
    AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(RestoreAttachmentJob.constructQueueString(RestoreAttachmentJob.RestoreOperation.RESTORE_OFFLOADED)))

    Log.i(TAG, "Optimizing media in the db")
    SignalDatabase.attachments.markEligibleAttachmentsAsOptimized()

    Log.i(TAG, "Deleting abandoned attachment files")
    SignalDatabase.attachments.deleteAbandonedAttachmentFiles()

    return Result.success()
  }

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  class Factory : Job.Factory<OptimizeMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): OptimizeMediaJob {
      return OptimizeMediaJob(parameters)
    }
  }
}
