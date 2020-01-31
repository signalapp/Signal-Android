package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BackgroundMessage private constructor(val data: Map<String, Any>) {

  companion object {

    @JvmStatic
    fun create(recipient: String) = BackgroundMessage(mapOf("recipient" to recipient))

    @JvmStatic
    fun createFriendRequest(recipient: String, messageBody: String) = BackgroundMessage(mapOf( "recipient" to recipient, "body" to messageBody, "friendRequest" to true ))

    @JvmStatic
    fun createUnpairingRequest(recipient: String) = BackgroundMessage(mapOf( "recipient" to recipient, "unpairingRequest" to true ))

    @JvmStatic
    fun createSessionRestore(recipient: String) = BackgroundMessage(mapOf( "recipient" to recipient, "friendRequest" to true, "sessionRestore" to true ))
            
    internal fun parse(serialized: String): BackgroundMessage {
      val data = JsonUtil.fromJson(serialized, Map::class.java) as? Map<String, Any> ?: throw AssertionError("JSON parsing failed")
      return BackgroundMessage(data)
    }
  }

  fun <T> get(key: String, defaultValue: T): T {
    return data[key] as? T ?: defaultValue
  }

  fun serialize(): String {
    return JsonUtil.toJson(data)
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
    val recipient = message.get<String?>("recipient", null) ?: throw IllegalStateException()
    val dataMessage = SignalServiceDataMessage.newBuilder()
            .withTimestamp(System.currentTimeMillis())
            .withBody(message.get<String?>("body", null))

    if (message.get("friendRequest", false)) {
      val bundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(recipient)
      dataMessage.withPreKeyBundle(bundle)
              .asFriendRequest(true)
    }

    if (message.get("unpairingRequest", false)) {
      dataMessage.asUnpairingRequest(true)
    }

    if (message.get("sessionRestore", false)) {
      dataMessage.asSessionRestore(true)
    }

    val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
    val address = SignalServiceAddress(recipient)
    try {
      val udAccess = UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.fromSerialized(recipient), false))
      messageSender.sendMessage(-1, address, udAccess, dataMessage.build()) // The message ID doesn't matter
    } catch (e: Exception) {
      Log.d("Loki", "Failed to send background message to: ${recipient}.")
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
