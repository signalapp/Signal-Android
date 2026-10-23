package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.concurrent.TimeUnit

/**
 * - Archives the session associated with the specified device
 * - Inserts an error message in the conversation
 * - Enqueues a [NullMessageSendJob] to trigger a fresh session with the specified device
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
    val timeSinceLastReset = System.currentTimeMillis() - SignalDatabase.recipients.getLastSessionResetTime(recipientId, deviceId)

    Log.i(TAG, "DeviceId: $deviceId, Reset interval: $resetInterval, Time since last reset: $timeSinceLastReset", true)

    if (timeSinceLastReset <= resetInterval) {
      Log.w(TAG, "Too soon! Time since last reset: $timeSinceLastReset", true)
      return Result.success()
    }

    Log.i(TAG, "We're good! Enqueuing a null message.", true)
    AppDependencies.jobManager.add(NullMessageSendJob.forSessionReset(recipientId, deviceId))

    return Result.success()
  }

  override fun onFailure() = Unit

  private fun insertLocalMessage() {
    val result = SignalDatabase.messages.insertChatSessionRefreshedMessage(recipientId, deviceId.toLong(), sentTimestamp - 1)
    AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(result.threadId))
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
