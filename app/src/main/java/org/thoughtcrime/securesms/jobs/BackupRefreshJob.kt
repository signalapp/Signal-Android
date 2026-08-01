/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import arrow.core.Either
import org.signal.core.util.logging.Log
import org.signal.network.service.ArchiveError
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Notifies the server that the backup for the local user is still being used.
 */
class BackupRefreshJob private constructor(
  parameters: Parameters
) : CoroutineJob(parameters) {

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

      if (!SignalStore.backup.areBackupsEnabled) {
        Log.i(TAG, "Backups have not been enabled on this device. Exiting.")
        return false
      }

      return true
    }
  }

  override suspend fun doRun(): Result {
    if (!canExecuteJob()) {
      return Result.success()
    }

    return when (val result = AppDependencies.archiveService.refreshBackup()) {
      is Either.Right -> {
        SignalStore.backup.lastCheckInMillis = System.currentTimeMillis()
        SignalStore.backup.lastCheckInSnoozeMillis = 0
        Result.success()
      }
      is Either.Left -> when (val error = result.value) {
        is ArchiveError.NetworkError -> {
          Log.w(TAG, "Network error when refreshing backup.", error.exception)
          Result.retry(defaultBackoff())
        }
        is ArchiveError.CredentialError.RateLimited -> {
          Log.w(TAG, "Rate limited when refreshing backup.", error.cause)
          Result.retry(error.retryAfter?.inWholeMilliseconds ?: defaultBackoff())
        }
        is ArchiveError.ApplicationError -> {
          Log.w(TAG, "Application error when refreshing backup.", error.exception)
          Result.failure()
        }
        is ArchiveError.CredentialError.Unauthorized,
        is ArchiveError.CredentialError.NotFound,
        is ArchiveError.CredentialError.InvalidRequest,
        is ArchiveError.CredentialError.ZkVerificationFailed -> {
          Log.w(TAG, "Error when refreshing backup: ${error::class.simpleName}", error.cause)
          Result.failure()
        }
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
