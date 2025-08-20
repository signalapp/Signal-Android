/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.NetworkResult

/**
 * Reserves backupIds for both text+media. The intention is that every registered user should be doing this, so it should happen post-registration
 * (as well as in a migration for pre-existing users).
 *
 * Calling this repeatedly is a no-op from the server's perspective, so no need to be careful around retries or anything.
 */
class ArchiveBackupIdReservationJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(ArchiveBackupIdReservationJob::class)

    const val KEY = "ArchiveBackupIdReservationJob"
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("ArchiveBackupIdReservationJob")
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    if (TextSecurePreferences.isUnauthorizedReceived(context)) {
      Log.w(TAG, "Not authorized. Skipping.")
      return Result.success()
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Linked device. Skipping.")
      return Result.success()
    }

    return when (val result = BackupRepository.triggerBackupIdReservation()) {
      is NetworkResult.Success -> Result.success()
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.ApplicationError -> Result.fatalFailure(RuntimeException(result.throwable))
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          429 -> Result.retry(result.retryAfter()?.inWholeMilliseconds ?: defaultBackoff())
          else -> {
            Log.w(TAG, "Failed to reserve backupId with status: ${result.code}. This should only happen on a malformed request or server error. Reducing backoff interval to be safe.")
            Result.retry(RemoteConfig.serverErrorMaxBackoff)
          }
        }
      }
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ArchiveBackupIdReservationJob> {
    override fun create(parameters: Parameters, data: ByteArray?): ArchiveBackupIdReservationJob {
      return ArchiveBackupIdReservationJob(parameters)
    }
  }
}
