package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BackgroundMessage private constructor(val recipient: String, val body: String?, val friendRequest: Boolean, val unpairingRequest: Boolean) {
  companion object {
    @JvmStatic
    fun create(recipient: String) = BackgroundMessage(recipient, null, false, false)
    @JvmStatic
    fun createFriendRequest(recipient: String, messageBody: String) = BackgroundMessage(recipient, messageBody, true, false)
    @JvmStatic
    fun createUnpairingRequest(recipient: String) = BackgroundMessage(recipient, null, false, true)
            
    internal fun parse(serialized: String): BackgroundMessage {
      val node = JsonUtil.fromJson(serialized)
      val recipient = node.get("recipient").asText()
      val body = if (node.hasNonNull("body")) node.get("body").asText() else null
      val friendRequest = node.get("friendRequest").asBoolean(false)
      val unpairingRequest = node.get("unpairingRequest").asBoolean(false)
      return BackgroundMessage(recipient, body, friendRequest, unpairingRequest)
    }
  }

  fun serialize(): String {
    val map = mapOf("recipient" to recipient, "body" to body, "friendRequest" to friendRequest, "unpairingRequest" to unpairingRequest)
    return JsonUtil.toJson(map)
  }
}

class PushBackgroundMessageSendJob private constructor(
    parameters: Parameters,
    private val message: BackgroundMessage
) : BaseJob(parameters) {
  companion object {
    const val KEY = "PushBackgroundMessageSendJob"

    private val TAG = PushBackgroundMessageSendJob::class.java.simpleName

    private val KEY_MESSAGE = "message"
  }

  constructor(message: BackgroundMessage) : this(Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(KEY)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setMaxAttempts(1)
          .build(),
          message)

  override fun serialize(): Data {
    return Data.Builder()
            .putString(KEY_MESSAGE, message.serialize())
            .build()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  public override fun onRun() {
    val dataMessage = SignalServiceDataMessage.newBuilder()
            .withTimestamp(System.currentTimeMillis())
            .withBody(message.body)

    if (message.friendRequest) {
      val bundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(message.recipient)
      dataMessage.withPreKeyBundle(bundle)
              .asFriendRequest(true)
    } else if (message.unpairingRequest) {
      dataMessage.asUnpairingRequest(true)
    }

    val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
    val address = SignalServiceAddress(message.recipient)
    try {
      messageSender.sendMessage(-1, address, Optional.absent<UnidentifiedAccessPair>(), dataMessage.build()) // The message ID doesn't matter
    } catch (e: Exception) {
      Log.d("Loki", "Failed to send background message to: ${message.recipient}.")
      throw e
    }
  }

  public override fun onShouldRetry(e: Exception): Boolean {
    // Loki - Disable since we have our own retrying when sending messages
    return false
  }

  override fun onCanceled() {}

  class Factory : Job.Factory<PushBackgroundMessageSendJob> {
    override fun create(parameters: Parameters, data: Data): PushBackgroundMessageSendJob {
      try {
        val messageJSON = data.getString(KEY_MESSAGE)
        return PushBackgroundMessageSendJob(parameters, BackgroundMessage.parse(messageJSON))
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
  }
}
