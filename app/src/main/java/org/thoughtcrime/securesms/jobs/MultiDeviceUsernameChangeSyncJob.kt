package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import kotlin.time.Duration.Companion.days

/**
 * Sends a sync message to alert linked devices of a username change so they can reset KT.
 */
class MultiDeviceUsernameChangeSyncJob private constructor(
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceUsernameChangeSyncJob"
    private val TAG = Log.tag(MultiDeviceUsernameChangeSyncJob::class.java)

    @WorkerThread
    @JvmStatic
    fun enqueueUsernameChangeSync() {
      if (!SignalStore.account.isMultiDevice) {
        return
      }

      AppDependencies.jobManager.add(
        MultiDeviceUsernameChangeSyncJob(
          parameters = Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setMaxAttempts(Parameters.UNLIMITED)
            .setLifespan(1.days.inWholeMilliseconds)
            .build()
        )
      )
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.w(TAG, "Not multi-device")
      return Result.failure()
    }

    Log.d(TAG, "Sending username change sync")
    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncUsernameChange(),
      urgent = true,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "username change sync", defaultBackoff())
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceUsernameChangeSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceUsernameChangeSyncJob {
      return MultiDeviceUsernameChangeSyncJob(parameters)
    }
  }
}
