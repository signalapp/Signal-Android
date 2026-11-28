/*
 * Copyright 2026 Signal Messenger, LLC
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
import org.thoughtcrime.securesms.net.SignalNetwork
import kotlin.time.Duration.Companion.days

/**
 * Routinely fetches archive credentials ahead of when they're needed, so that the authenticated fetch isn't correlated with the anonymous requests that use them.
 */
class ArchiveCredentialRefreshJob private constructor(
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(ArchiveCredentialRefreshJob::class)
    const val KEY = "ArchiveCredentialRefreshJob"

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!canExecuteJob()) {
        return
      }

      AppDependencies.jobManager.add(
        ArchiveCredentialRefreshJob(
          parameters = Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setMaxAttempts(Parameters.UNLIMITED)
            .setLifespan(1.days.inWholeMilliseconds)
            .setMaxInstancesForFactory(1)
            .build()
        )
      )
    }

    private fun canExecuteJob(): Boolean {
      if (!SignalStore.account.isRegistered) {
        Log.i(TAG, "Account not registered. Exiting.")
        return false
      }

      if (SignalStore.account.isLinkedDevice) {
        Log.i(TAG, "Linked device. Exiting.")
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

    return when (val result = SignalNetwork.archiveService.refreshServiceCredentialsIfNecessary()) {
      is Either.Right -> {
        Result.success()
      }
      is Either.Left -> when (val error = result.value) {
        is ArchiveError.NetworkError -> {
          Log.w(TAG, "Network error when refreshing credentials.", error.exception)
          Result.retry(defaultBackoff())
        }
        is ArchiveError.CredentialError.RateLimited -> {
          Log.w(TAG, "Rate limited when refreshing credentials.", error.cause)
          Result.retry(error.retryAfter?.inWholeMilliseconds ?: defaultBackoff())
        }
        is ArchiveError.ApplicationError -> {
          Log.w(TAG, "Application error when refreshing credentials.", error.exception)
          Result.failure()
        }
        is ArchiveError.CredentialError.Unauthorized,
        is ArchiveError.CredentialError.NotFound,
        is ArchiveError.CredentialError.InvalidRequest,
        is ArchiveError.CredentialError.ZkVerificationFailed -> {
          Log.w(TAG, "Error when refreshing credentials: ${error::class.simpleName}", error.cause)
          Result.failure()
        }
      }
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<ArchiveCredentialRefreshJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveCredentialRefreshJob {
      return ArchiveCredentialRefreshJob(parameters)
    }
  }
}
