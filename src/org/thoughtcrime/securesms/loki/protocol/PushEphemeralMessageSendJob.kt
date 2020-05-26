package org.thoughtcrime.securesms.loki.protocol

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
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.util.concurrent.TimeUnit

class PushEphemeralMessageSendJob private constructor(parameters: Parameters, private val message: EphemeralMessage) : BaseJob(parameters) {

    companion object {
        private const val KEY_MESSAGE = "message"
        const val KEY = "PushBackgroundMessageSendJob"
    }

    constructor(message: EphemeralMessage) : this(Parameters.Builder()
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

    override fun getFactoryKey(): String { return KEY }

    public override fun onRun() {
        val recipient = message.get<String?>("recipient", null) ?: throw IllegalStateException()
        val dataMessage = SignalServiceDataMessage.newBuilder().withTimestamp(System.currentTimeMillis())
        // Attach a pre key bundle if needed
        if (message.get("friendRequest", false)) {
            val bundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(recipient)
            dataMessage.withPreKeyBundle(bundle).asFriendRequest(true)
        }
        // Set flags if needed (these are mutually exclusive)
        when {
            message.get("unpairingRequest", false) -> dataMessage.asUnlinkingRequest(true)
            message.get("sessionRestore", false) -> dataMessage.asSessionRestorationRequest(true)
            message.get("sessionRequest", false) -> dataMessage.asSessionRequest(true)
        }
        // Send the message
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(recipient)
        try {
            val udAccess = UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.fromSerialized(recipient), false))
            messageSender.sendMessage(0, address, udAccess, dataMessage.build()) // The message ID doesn't matter
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send background message to: $recipient due to error: $e.")
            throw e
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        // Disable since we have our own retrying
        return false
    }

    override fun onCanceled() { }

    class Factory : Job.Factory<PushEphemeralMessageSendJob> {

      override fun create(parameters: Parameters, data: Data): PushEphemeralMessageSendJob {
          try {
              val messageJSON = data.getString(KEY_MESSAGE)
              return PushEphemeralMessageSendJob(parameters, EphemeralMessage.parse(messageJSON))
          } catch (e: IOException) {
              throw AssertionError(e)
          }
      }
    }
}
