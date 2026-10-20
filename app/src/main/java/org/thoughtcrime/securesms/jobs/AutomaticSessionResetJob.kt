package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.DeviceLastResetTime
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.util.OutgoingContent
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.concurrent.TimeUnit

/**
 * - Archives the session associated with the specified device
 * - Inserts an error message in the conversation
 * - Sends a new, empty message to trigger a fresh session with the specified device
 *
 * This will only be run when all decryptions have finished, and there can only be one enqueued
 * per websocket drain cycle.
 */
class AutomaticSessionResetJob private constructor(
  parameters: Parameters,
  private val recipientId: RecipientId,
  private val deviceId: Int,
  private val sentTimestamp: Long
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(AutomaticSessionResetJob::class.java)

    const val KEY: String = "AutomaticSessionResetJob"

    private const val KEY_RECIPIENT_ID = "recipient_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SENT_TIMESTAMP = "sent_timestamp"
  }

  constructor(recipientId: RecipientId, deviceId: Int, sentTimestamp: Long) : this(
    parameters = Parameters.Builder()
      .setQueue(PushProcessMessageJob.getQueueName(recipientId))
      .addConstraint(DecryptionsDrainedConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMaxInstancesForQueue(1)
      .build(),
    recipientId = recipientId,
    deviceId = deviceId,
    sentTimestamp = sentTimestamp
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .putInt(KEY_DEVICE_ID, deviceId)
      .putLong(KEY_SENT_TIMESTAMP, sentTimestamp)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (recipientId.isUnknown) {
      Log.w(TAG, "Recipient is unknown! Skipping session reset.", true)
      return Result.success()
    }

    AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId, deviceId)
    SignalDatabase.senderKeyShared.deleteAllFor(recipientId)
    insertLocalMessage()

    if (!RemoteConfig.automaticSessionReset) {
      Log.w(TAG, "Automatic session reset send disabled!", true)
      return Result.success()
    }

    val resetInterval = TimeUnit.SECONDS.toMillis(RemoteConfig.automaticSessionResetIntervalSeconds.toLong())
    val resetTimes = SignalDatabase.recipients.getLastSessionResetTimes(recipientId)
    val timeSinceLastReset = System.currentTimeMillis() - getLastResetTime(resetTimes, deviceId)

    Log.i(TAG, "DeviceId: $deviceId, Reset interval: $resetInterval, Time since last reset: $timeSinceLastReset", true)

    if (timeSinceLastReset <= resetInterval) {
      Log.w(TAG, "Too soon! Time since last reset: $timeSinceLastReset", true)
      return Result.success()
    }

    Log.i(TAG, "We're good! Sending a null message.", true)

    val now = System.currentTimeMillis()
    SignalDatabase.recipients.setLastSessionResetTime(recipientId, setLastResetTime(resetTimes, deviceId, now))
    Log.i(TAG, "Marked last reset time: $now", true)

    return sendNullMessage()
  }

  override fun onFailure() = Unit

  private fun insertLocalMessage() {
    val result = SignalDatabase.messages.insertChatSessionRefreshedMessage(recipientId, deviceId.toLong(), sentTimestamp - 1)
    AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(result.threadId))
  }

  private suspend fun sendNullMessage(): Result {
    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.registered == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.success()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    val result = if (serviceId == SignalStore.account.aci) {
      SignalNetwork.messageService.sendSyncMessage(
        timestamp = System.currentTimeMillis(),
        envelopeContent = OutgoingContent.nullMessage(),
        urgent = false,
        onEncrypted = null
      )
    } else {
      SignalNetwork.messageService.sendMessage(
        serviceId = serviceId,
        envelopeContent = OutgoingContent.nullMessage(),
        timestamp = System.currentTimeMillis(),
        sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
        story = false,
        isOnline = false,
        urgent = false
      )
    }

    return result.fold(
      ifRight = {
        Log.i(TAG, "Successfully sent!", true)
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is MessageService.SendError.IdentityMismatch -> {
            Log.w(TAG, "Unable to send null message due to an identity mismatch.")
            Result.success()
          }

          is MessageService.SendError.ApplicationError -> when (val cause = error.exception) {
            is NoSessionException,
            is InvalidSessionException -> {
              Log.w(TAG, "Unable to send null message; no session to send over.", cause)
              Result.success()
            }

            is RuntimeException -> {
              Log.e(TAG, "Unexpected error sending null message.", cause)
              Result.fatalFailure(cause)
            }

            else -> {
              Log.w(TAG, "Failed to send null message.", cause)
              Result.failure()
            }
          }

          else -> {
            Log.w(TAG, "Failed to send null message.", error)
            Result.failure()
          }
        }
      }
    )
  }

  private fun getLastResetTime(resetTimes: DeviceLastResetTime, deviceId: Int): Long {
    return resetTimes.resetTime.firstOrNull { it.deviceId == deviceId }?.lastResetTime ?: 0
  }

  private fun setLastResetTime(resetTimes: DeviceLastResetTime, deviceId: Int, time: Long): DeviceLastResetTime {
    val newResetTimes = resetTimes.resetTime.filter { it.deviceId != deviceId } + DeviceLastResetTime.Pair(deviceId = deviceId, lastResetTime = time)
    return DeviceLastResetTime(resetTime = newResetTimes)
  }

  class Factory : Job.Factory<AutomaticSessionResetJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AutomaticSessionResetJob {
      val data = JsonJobData.deserialize(serializedData)

      return AutomaticSessionResetJob(
        parameters = parameters,
        recipientId = RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
        deviceId = data.getInt(KEY_DEVICE_ID),
        sentTimestamp = data.getLong(KEY_SENT_TIMESTAMP)
      )
    }
  }
}
