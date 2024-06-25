/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.RemoteConfigResult
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * If we have reason to believe a build is expired, we run this job to double-check by fetching the server time. This prevents false positives from people
 * moving their clock forward in time.
 */
class BuildExpirationConfirmationJob private constructor(params: Parameters) : Job(params) {
  companion object {
    const val KEY = "BuildExpirationConfirmationJob"
    private val TAG = Log.tag(BuildExpirationConfirmationJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(2)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (Util.getTimeUntilBuildExpiry(SignalStore.misc.estimatedServerTime) > 0) {
      Log.i(TAG, "Build not expired.", true)
      return Result.success()
    }

    if (SignalStore.misc.isClientDeprecated) {
      Log.i(TAG, "Build already marked expired. Nothing to do.", true)
      return Result.success()
    }

    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Can't check the server time, so assuming deprecated.", true)
      SignalStore.misc.isClientDeprecated = true
      return Result.success()
    }

    val result: NetworkResult<RemoteConfigResult> = NetworkResult.fromFetch {
      AppDependencies.signalServiceAccountManager.remoteConfig
    }

    return when (result) {
      is NetworkResult.Success -> {
        val serverTimeMs = result.result.serverEpochTimeSeconds.seconds.inWholeMilliseconds
        SignalStore.misc.setLastKnownServerTime(serverTimeMs, System.currentTimeMillis())

        if (Util.getTimeUntilBuildExpiry(serverTimeMs) <= 0) {
          Log.w(TAG, "Build confirmed expired! Server time: $serverTimeMs, Local time: ${System.currentTimeMillis()}, Build time: ${BuildConfig.BUILD_TIMESTAMP}, Time since expiry: ${serverTimeMs - BuildConfig.BUILD_TIMESTAMP}", true)
          SignalStore.misc.isClientDeprecated = true
        } else {
          Log.w(TAG, "Build not actually expired! Likely bad local clock. Server time: $serverTimeMs, Local time: ${System.currentTimeMillis()}, Build time: ${BuildConfig.BUILD_TIMESTAMP}")
        }
        Result.success()
      }
      is NetworkResult.ApplicationError -> Result.retry(defaultBackoff())
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError -> if (result.code < 500) Result.retry(defaultBackoff()) else Result.success()
    }
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<BuildExpirationConfirmationJob> {
    override fun create(params: Parameters, bytes: ByteArray?): BuildExpirationConfirmationJob {
      return BuildExpirationConfirmationJob(params)
    }
  }
}
