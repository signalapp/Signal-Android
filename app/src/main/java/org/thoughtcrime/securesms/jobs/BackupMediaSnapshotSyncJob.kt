/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupMediaSnapshotSyncJobData
import org.whispersystems.signalservice.api.NetworkResult

/**
 * Synchronizes the server media via bulk deletions of old attachments not present
 * in the user's current backup.
 */
class BackupMediaSnapshotSyncJob private constructor(private val syncTime: Long, parameters: Parameters) : Job(parameters) {

  companion object {

    private val TAG = Log.tag(BackupMediaSnapshotSyncJob::class)

    const val KEY = "BackupMediaSnapshotSyncJob"

    private const val PAGE_SIZE = 500

    fun enqueue(backupSnapshotId: Long) {
      AppDependencies.jobManager.add(
        BackupMediaSnapshotSyncJob(
          backupSnapshotId,
          Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setMaxInstancesForFactory(1)
            .build()
        )
      )
    }
  }

  override fun serialize(): ByteArray = BackupMediaSnapshotSyncJobData(syncTime).encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    while (SignalDatabase.backupMediaSnapshots.hasOldMediaObjects(syncTime)) {
      val mediaObjects = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(syncTime, PAGE_SIZE)

      when (val networkResult = BackupRepository.deleteAbandonedMediaObjects(mediaObjects)) {
        is NetworkResult.Success -> {
          SignalDatabase.backupMediaSnapshots.deleteMediaObjects(mediaObjects)
        }

        else -> {
          Log.w(TAG, "Failed to delete media objects.", networkResult.getCause())
          return Result.failure()
        }
      }
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<BackupMediaSnapshotSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMediaSnapshotSyncJob {
      val syncTime: Long = BackupMediaSnapshotSyncJobData.ADAPTER.decode(serializedData!!).syncTime

      return BackupMediaSnapshotSyncJob(syncTime, parameters)
    }
  }
}
