/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Notifies the server that the backup for the local user is still being used.
 */
class BackupRefreshJob private constructor(
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(BackupRefreshJob::class)
    const val KEY = "BackupRefreshJob"

    private val TIME_BETWEEN_CHECKINS = 1.days

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!canExecuteJob()) {
        return
      }

      val now = System.currentTimeMillis().milliseconds
      val lastCheckIn = SignalStore.backup.lastCheckInMillis.milliseconds

      if ((now - lastCheckIn) >= TIME_BETWEEN_CHECKINS) {
        AppDependencies.jobManager.add(
          BackupRefreshJob(
            parameters = Parameters.Builder()
              .addConstraint(NetworkConstraint.KEY)
              .setMaxAttempts(Parameters.UNLIMITED)
              .setLifespan(1.days.inWholeMilliseconds)
              .setMaxInstancesForFactory(1)
              .build()
          )
        )
      } else {
        Log.i(TAG, "Do not need to refresh backups. Last refresh: ${lastCheckIn.inWholeMilliseconds}")
      }
    }

    private fun canExecuteJob(): Boolean {
      if (!SignalStore.account.isRegistered) {
        Log.i(TAG, "Account not registered. Exiting.")
        return false
      }

      if (!RemoteConfig.messageBackups) {
        Log.i(TAG, "Backups are not enabled in remote config. Exiting.")
        return false
      }

      if (!SignalStore.backup.areBackupsEnabled) {
        Log.i(TAG, "Backups have not been enabled on this device. Exiting.")
        return false
      }

      return true
    }
  }

  override fun run(): Result {
    if (!canExecuteJob()) {
      return Result.success()
    }

    val result = BackupRepository.refreshBackup()

    return when (result) {
      is NetworkResult.Success -> {
        SignalStore.backup.lastCheckInMillis = System.currentTimeMillis()
        SignalStore.backup.lastCheckInSnoozeMillis = 0
        Result.success()
      }
      else -> {
        Log.w(TAG, "Failed to refresh backup with server.", result.getCause())
        Result.failure()
      }
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<BackupRefreshJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupRefreshJob {
      return BackupRefreshJob(parameters)
    }
  }
}
