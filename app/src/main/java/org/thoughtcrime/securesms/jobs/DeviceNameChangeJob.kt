package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.DeviceNameChangeJobData
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

/**
 * Sends a sync message that a linked device has changed its name
 */
class DeviceNameChangeJob private constructor(
  private val data: DeviceNameChangeJobData,
  parameters: Parameters
) : CoroutineJob(parameters) {
  companion object {
    const val KEY: String = "DeviceNameChangeJob"
    private val TAG = Log.tag(DeviceNameChangeJob::class.java)
  }

  constructor(
    deviceId: Int
  ) : this(
    DeviceNameChangeJobData(deviceId),
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("DeviceNameChangeJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray {
    return data.encode()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncDeviceNameChange(SyncMessage.DeviceNameChange(data.deviceId)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "device name sync", defaultBackoff())
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<DeviceNameChangeJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DeviceNameChangeJob {
      return DeviceNameChangeJob(DeviceNameChangeJobData.ADAPTER.decode(serializedData!!), parameters)
    }
  }
}
