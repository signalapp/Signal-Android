package org.thoughtcrime.securesms.loki.protocol

import com.google.protobuf.ByteString
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
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

class PushSessionRequestMessageSendJob private constructor(parameters: Parameters, private val publicKey: String) : BaseJob(parameters) {

    companion object {
        const val KEY = "PushSessionRequestMessageSendJob"
    }

    constructor(publicKey: String) : this(Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(1)
            .build(),
            publicKey)

    override fun serialize(): Data {
        return Data.Builder().putString("publicKey", publicKey).build()
    }

    override fun getFactoryKey(): String { return KEY }

    public override fun onRun() {
        // Prepare
        val contentMessage = SignalServiceProtos.Content.newBuilder()
        // Attach the pre key bundle message
        val preKeyBundleMessage = SignalServiceProtos.PreKeyBundleMessage.newBuilder()
        val preKeyBundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(publicKey) ?: return
        preKeyBundleMessage.identityKey = ByteString.copyFrom(preKeyBundle.identityKey.serialize())
        preKeyBundleMessage.deviceId = preKeyBundle.deviceId
        preKeyBundleMessage.preKeyId = preKeyBundle.preKeyId
        preKeyBundleMessage.signedKeyId = preKeyBundle.signedPreKeyId
        preKeyBundleMessage.preKey = ByteString.copyFrom(preKeyBundle.preKey.serialize())
        preKeyBundleMessage.signedKey = ByteString.copyFrom(preKeyBundle.signedPreKey.serialize())
        preKeyBundleMessage.signature = ByteString.copyFrom(preKeyBundle.signedPreKeySignature)
        contentMessage.preKeyBundleMessage = preKeyBundleMessage.build()
        // Attach the null message
        val nullMessage = SignalServiceProtos.NullMessage.newBuilder()
        val sr = SecureRandom()
        val paddingSize = sr.nextInt(512)
        val padding = ByteArray(paddingSize)
        sr.nextBytes(padding)
        nullMessage.padding = ByteString.copyFrom(padding)
        contentMessage.nullMessage = nullMessage.build()
        // Send the result
        val serializedContentMessage = contentMessage.build().toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(publicKey)
        val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        val ttl = TTLUtilities.getTTL(TTLUtilities.MessageType.SessionRequest)
        try {
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedContentMessage, false, ttl, false,
                    true, false, false)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send session request to: $publicKey due to error: $e.")
            throw e
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        // Disable since we have our own retrying
        return false
    }

    override fun onCanceled() { }

    class Factory : Job.Factory<PushSessionRequestMessageSendJob> {

        override fun create(parameters: Parameters, data: Data): PushSessionRequestMessageSendJob {
            try {
                val publicKey = data.getString("publicKey")
                return PushSessionRequestMessageSendJob(parameters, publicKey)
            } catch (e: IOException) {
                throw AssertionError(e)
            }
        }
    }
}
