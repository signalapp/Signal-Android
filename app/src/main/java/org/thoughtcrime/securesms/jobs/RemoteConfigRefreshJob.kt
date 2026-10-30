package org.thoughtcrime.securesms.jobs

import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RemoteConfigApi.RemoteConfigResult
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import kotlin.time.Duration.Companion.days

/**
 * Job to refresh remote configs. Utilizes eTags so a 304 is returned if content is unchanged since last fetch.
 */
class RemoteConfigRefreshJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {
  companion object {
    const val KEY: String = "RemoteConfigRefreshJob"
    private val TAG = Log.tag(RemoteConfigRefreshJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override suspend fun doRun(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    if (SignalStore.account.isUnauthorizedReceived) {
      Log.i(TAG, "No longer authorized. Ignoring.")
      return Result.success()
    }

    return when (val result = SignalNetwork.remoteConfigApi.getRemoteConfig(SignalStore.remoteConfig.eTag)) {
      is RequestResult.Success -> {
        when (val config = result.result) {
          is RemoteConfigResult.Updated -> {
            RemoteConfig.update(config.config)
            if (config.eTag.isNotNullOrBlank()) {
              SignalStore.remoteConfig.eTag = config.eTag
            }
          }
          is RemoteConfigResult.NotModified -> {
            Log.i(TAG, "Remote config has not changed since last pull.")
            SignalStore.remoteConfig.lastFetchTime = System.currentTimeMillis()
          }
        }
        SignalStore.misc.setLastKnownServerTime(result.result.serverEpochTimeMilliseconds, System.currentTimeMillis())
        Result.success()
      }
      is RequestResult.NonSuccess -> {
        Result.retry(defaultBackoff())
      }
      is RequestResult.RetryableNetworkError -> {
        Result.retry(result.retryAfter?.toMillis() ?: defaultBackoff())
      }
      is RequestResult.ApplicationError -> {
        Result.failure()
      }
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RemoteConfigRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RemoteConfigRefreshJob {
      return RemoteConfigRefreshJob(parameters)
    }
  }
}
