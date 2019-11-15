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
import java.io.IOException
import java.util.concurrent.TimeUnit

class PushBackgroundMessageSendJob private constructor(
    parameters: Parameters,
    private val recipient: String,
    private val messageBody: String?,
    private val friendRequest: Boolean
) : BaseJob(parameters) {
  companion object {
    const val KEY = "PushBackgroundMessageSendJob"

    private val TAG = PushBackgroundMessageSendJob::class.java.simpleName

    private val KEY_RECIPIENT = "recipient"
    private val KEY_MESSAGE_BODY = "message_body"
    private val KEY_FRIEND_REQUEST = "asFriendRequest"
  }

  constructor(recipient: String): this(recipient, null, false)
  constructor(recipient: String, messageBody: String?, friendRequest: Boolean) : this(Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(KEY)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setMaxAttempts(1)
          .build(),
          recipient, messageBody, friendRequest)

  override fun serialize(): Data {
    return Data.Builder()
            .putString(KEY_RECIPIENT, recipient)
            .putString(KEY_MESSAGE_BODY, messageBody)
            .putBoolean(KEY_FRIEND_REQUEST, friendRequest)
            .build()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  public override fun onRun() {
    val message = SignalServiceDataMessage.newBuilder()
            .withTimestamp(System.currentTimeMillis())
            .withBody(messageBody)

    if (friendRequest) {
      val bundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(recipient)
      message.withPreKeyBundle(bundle)
              .asFriendRequest(true)
    }

    val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
    val address = SignalServiceAddress(recipient)
    try {
      messageSender.sendMessage(-1, address, Optional.absent<UnidentifiedAccessPair>(), message.build()) // The message ID doesn't matter
    } catch (e: Exception) {
      Log.d("Loki", "Failed to send background message to: $recipient.")
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
        val recipient = data.getString(KEY_RECIPIENT)
        val messageBody = if (data.hasString(KEY_MESSAGE_BODY)) data.getString(KEY_MESSAGE_BODY) else null
        val friendRequest = data.getBooleanOrDefault(KEY_FRIEND_REQUEST, false)
        return PushBackgroundMessageSendJob(parameters, recipient, messageBody, friendRequest)
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
  }
}
