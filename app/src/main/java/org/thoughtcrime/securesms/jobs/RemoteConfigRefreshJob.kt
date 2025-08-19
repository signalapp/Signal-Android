package org.thoughtcrime.securesms.jobs

import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import kotlin.time.Duration.Companion.days

/**
 * Job to refresh remote configs. Utilizes eTags so a 304 is returned if content is unchanged since last fetch.
 */
class RemoteConfigRefreshJob private constructor(parameters: Parameters) : Job(parameters) {
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

  override fun run(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    return when (val result = SignalNetwork.remoteConfig.getRemoteConfig(SignalStore.remoteConfig.eTag)) {
      is NetworkResult.Success -> {
        RemoteConfig.update(result.result.config)
        SignalStore.misc.setLastKnownServerTime(result.result.serverEpochTimeMilliseconds, System.currentTimeMillis())
        if (result.result.eTag.isNotNullOrBlank()) {
          SignalStore.remoteConfig.eTag = result.result.eTag
        }
        Result.success()
      }

      is NetworkResult.ApplicationError -> Result.failure()
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError ->
        if (result.code == 304) {
          Log.i(TAG, "Remote config has not changed since last pull.")
          Result.success()
        } else {
          Result.retry(defaultBackoff())
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
