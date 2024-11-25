package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.DeviceNameChangeJobData
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends a sync message that a linked device has changed its name
 */
class DeviceNameChangeJob private constructor(
  private val data: DeviceNameChangeJobData,
  parameters: Parameters
) : Job(parameters) {
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
      .setQueue("DeviceNameChangeJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray {
    return data.encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    return try {
      val result = AppDependencies.signalServiceMessageSender.sendSyncMessage(
        SignalServiceSyncMessage.forDeviceNameChange(SyncMessage.DeviceNameChange(data.deviceId))
      )
      if (result.isSuccess) {
        Result.success()
      } else {
        Log.w(TAG, "Unable to send device name sync - trying later")
        Result.retry(defaultBackoff())
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to send device name sync - trying later", e)
      Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, "Unable to send device name sync", e)
      Result.failure()
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<DeviceNameChangeJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DeviceNameChangeJob {
      return DeviceNameChangeJob(DeviceNameChangeJobData.ADAPTER.decode(serializedData!!), parameters)
    }
  }
}
