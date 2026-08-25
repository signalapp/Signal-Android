/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import arrow.core.Either
import org.signal.core.util.logging.Log
import org.signal.network.service.ArchiveError
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupTierDowngradeCheckJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * Asks the service what tier we're actually on after a storage service account record told us our backup tier went down.
 *
 * [remoteBackupTier] is the zkgroup backup level from that record, which we fall back to when the service can't give us a usable answer.
 */
class BackupTierDowngradeCheckJob private constructor(
  private val remoteBackupTier: Long?,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupTierDowngradeCheckJob::class)

    const val KEY = "BackupTierDowngradeCheckJob"

    fun enqueue(remoteBackupTier: Long?) {
      AppDependencies.jobManager.add(create(remoteBackupTier))
    }

    @VisibleForTesting
    fun create(remoteBackupTier: Long?): BackupTierDowngradeCheckJob {
      return BackupTierDowngradeCheckJob(
        remoteBackupTier = remoteBackupTier,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(3.days.inWholeMilliseconds)
          .setMaxInstancesForFactory(1)
          .build()
      )
    }
  }

  override suspend fun doRun(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "Not registered. Nothing to confirm.")
      return Result.success()
    }

    if (SignalStore.account.isPrimaryDevice) {
      Log.i(TAG, "Primary device owns its own tier. Nothing to confirm.")
      return Result.success()
    }

    if (SignalStore.backup.backupTier == null) {
      Log.i(TAG, "We already have no tier. Nothing to confirm.")
      return Result.success()
    }

    return when (val result = BackupRepository.getBackupTierWithoutDowngrade()) {
      is Either.Right -> {
        Log.i(TAG, "Service says we're on ${result.value}. Applying it over our local tier of ${SignalStore.backup.backupTier}.", true)
        SignalStore.backup.backupTier = result.value
        Result.success()
      }

      is Either.Left -> handleError(result.value)
    }
  }

  private fun handleError(error: ArchiveError.CredentialError): Result {
    return when (error) {
      is ArchiveError.NetworkError -> {
        Log.w(TAG, "Network error. Retrying later.")
        Result.retry(defaultBackoff())
      }

      is ArchiveError.CredentialError.RateLimited -> {
        Log.w(TAG, "Rate limited. Retrying later.")
        Result.retry(error.retryAfter?.inWholeMilliseconds ?: defaultBackoff())
      }

      is ArchiveError.CredentialError.NotFound,
      is ArchiveError.CredentialError.Unauthorized,
      is ArchiveError.CredentialError.InvalidRequest,
      is ArchiveError.CredentialError.ZkVerificationFailed,
      is ArchiveError.ApplicationError -> {
        val tier = MessageBackupTier.fromBackupLevel(remoteBackupTier)
        Log.w(TAG, "Service could not tell us our tier ($error). Deferring to the account record and applying $tier over ${SignalStore.backup.backupTier}.", true)
        SignalStore.backup.backupTier = tier
        Result.success()
      }
    }
  }

  override fun serialize(): ByteArray {
    return BackupTierDowngradeCheckJobData(remoteBackupTier = remoteBackupTier).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<BackupTierDowngradeCheckJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupTierDowngradeCheckJob {
      val data = BackupTierDowngradeCheckJobData.ADAPTER.decode(serializedData!!)
      return BackupTierDowngradeCheckJob(remoteBackupTier = data.remoteBackupTier, parameters = parameters)
    }
  }
}
