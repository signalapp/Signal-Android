/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore

class CancelRestoreMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(CancelRestoreMediaJob::class)
    const val KEY = "CancelRestoreMediaJob"

    fun enqueue() {
      AppDependencies.jobManager.add(
        CancelRestoreMediaJob(parameters = Parameters.Builder().build())
      )
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    SignalStore.backup.userManuallySkippedMediaRestore = true

    ArchiveRestoreProgress.onCancelMediaRestore()

    Log.i(TAG, "Canceling all media restore jobs")
    RestoreAttachmentJob.Queues.ALL.forEach { AppDependencies.jobManager.cancelAllInQueue(it) }

    Log.i(TAG, "Enqueueing check restore media jobs to cleanup")
    RestoreAttachmentJob.Queues.ALL.forEach { AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(it)) }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<CancelRestoreMediaJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CancelRestoreMediaJob {
      return CancelRestoreMediaJob(parameters = parameters)
    }
  }
}
