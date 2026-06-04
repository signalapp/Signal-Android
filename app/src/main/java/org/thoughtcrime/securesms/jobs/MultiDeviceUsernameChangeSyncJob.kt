package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.pad
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.IOException
import java.util.Optional
import kotlin.time.Duration.Companion.days

/**
 * Sends a sync message to alert linked devices of a username change so they can reset KT.
 */
class MultiDeviceUsernameChangeSyncJob private constructor(
  parameters: Parameters
) : Job(parameters) {

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

  override fun run(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.w(TAG, "Not multi-device")
      return Result.failure()
    }

    val syncMessageContent = Content(
      syncMessage = SyncMessage.Builder()
        .pad()
        .usernameChange(SyncMessage.UsernameChange())
        .build()
    )

    return try {
      Log.d(TAG, "Sending username change sync")
      val success = AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessageContent, true, Optional.empty()).isSuccess
      if (success) {
        Result.success()
      } else {
        Log.w(TAG, "Unsuccessful username change send. Retrying.")
        Result.retry(defaultBackoff())
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to send username change sync due to io exception", e)
      Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, "Unable to send username change sync due to untrusted exception", e)
      Result.failure()
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceUsernameChangeSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceUsernameChangeSyncJob {
      return MultiDeviceUsernameChangeSyncJob(parameters)
    }
  }
}
