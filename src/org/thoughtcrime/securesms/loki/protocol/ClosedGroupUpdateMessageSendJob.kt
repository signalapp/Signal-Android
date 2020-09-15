package org.thoughtcrime.securesms.loki.protocol

import com.google.protobuf.ByteString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.util.Hex
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities
import org.whispersystems.signalservice.loki.utilities.toHexString
import java.util.*
import java.util.concurrent.TimeUnit

class ClosedGroupUpdateMessageSendJob private constructor(parameters: Parameters, private val destination: String, private val kind: Kind) : BaseJob(parameters) {

    sealed class Kind {
        class New(val groupPublicKey: ByteArray, val name: String, val groupPrivateKey: ByteArray, val senderKeys: Collection<ClosedGroupSenderKey>, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class Info(val groupPublicKey: ByteArray, val name: String, val senderKeys: Collection<ClosedGroupSenderKey>, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class SenderKeyRequest(val groupPublicKey: ByteArray) : Kind()
        class SenderKey(val groupPublicKey: ByteArray, val senderKey: ClosedGroupSenderKey) : Kind()
    }

    companion object {
        const val KEY = "ClosedGroupUpdateMessageSendJob"
    }

    constructor(destination: String, kind: Kind) : this(Parameters.Builder()
        .addConstraint(NetworkConstraint.KEY)
        .setQueue(KEY)
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(1)
        .build(),
        destination,
        kind)

    override fun getFactoryKey(): String { return KEY }

    override fun serialize(): Data {
        val builder = Data.Builder()
        builder.putString("destination", destination)
        when (kind) {
            is Kind.New -> {
                builder.putString("kind", "New")
                builder.putByteArray("groupPublicKey", kind.groupPublicKey)
                builder.putString("name", kind.name)
                builder.putByteArray("groupPrivateKey", kind.groupPrivateKey)
                val senderKeys = kind.senderKeys.joinToString(" - ") { it.toJSON() }
                builder.putString("senderKeys", senderKeys)
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
                val admins = kind.admins.joinToString(" - ") { it.toHexString() }
                builder.putString("admins", admins)
            }
            is Kind.Info -> {
                builder.putString("kind", "Info")
                builder.putByteArray("groupPublicKey", kind.groupPublicKey)
                builder.putString("name", kind.name)
                val senderKeys = kind.senderKeys.joinToString(" - ") { it.toJSON() }
                builder.putString("senderKeys", senderKeys)
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
                val admins = kind.admins.joinToString(" - ") { it.toHexString() }
                builder.putString("admins", admins)
            }
            is Kind.SenderKeyRequest -> {
                builder.putString("kind", "SenderKeyRequest")
                builder.putByteArray("groupPublicKey", kind.groupPublicKey)
            }
            is Kind.SenderKey -> {
                builder.putString("kind", "SenderKey")
                builder.putByteArray("groupPublicKey", kind.groupPublicKey)
                builder.putString("senderKey", kind.senderKey.toJSON())
            }
        }
        return builder.build()
    }

    public override fun onRun() {
        val contentMessage = SignalServiceProtos.Content.newBuilder()
        val dataMessage = SignalServiceProtos.DataMessage.newBuilder()
        val closedGroupUpdate = SignalServiceProtos.ClosedGroupUpdate.newBuilder()
        when (kind) {
            is Kind.New -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.NEW
                closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                closedGroupUpdate.name = kind.name
                closedGroupUpdate.groupPrivateKey = ByteString.copyFrom(kind.groupPrivateKey)
                closedGroupUpdate.addAllSenderKeys(kind.senderKeys.map { it.toProto() })
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
            }
            is Kind.Info -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.INFO
                closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                closedGroupUpdate.name = kind.name
                closedGroupUpdate.addAllSenderKeys(kind.senderKeys.map { it.toProto() })
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
            }
            is Kind.SenderKeyRequest -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY_REQUEST
                closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
            }
            is Kind.SenderKey -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY
                closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                closedGroupUpdate.addAllSenderKeys(listOf( kind.senderKey.toProto() ))
            }
        }
        dataMessage.closedGroupUpdate = closedGroupUpdate.build()
        contentMessage.dataMessage = dataMessage.build()
        val serializedContentMessage = contentMessage.build().toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(destination)
        val recipient = recipient(context, destination)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        val ttl = TTLUtilities.getTTL(TTLUtilities.MessageType.ClosedGroupUpdate)
        val useFallbackEncryption = SignalProtocolStoreImpl(context).containsSession(SignalProtocolAddress(destination, 1))
        try {
            // isClosedGroup can always be false as it's only used in the context of legacy closed groups
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedContentMessage, false, ttl, false,
                    useFallbackEncryption, false, false)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send closed group update message to: $destination due to error: $e.")
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        // Disable since we have our own retrying
        return false
    }

    override fun onCanceled() { }

    class Factory : Job.Factory<ClosedGroupUpdateMessageSendJob> {

        override fun create(parameters: Parameters, data: Data): ClosedGroupUpdateMessageSendJob {
            val destination = data.getString("destination")
            val rawKind = data.getString("kind")
            val groupPublicKey = data.getByteArray("groupPublicKey")
            val kind: Kind
            when (rawKind) {
                "New" -> {
                    val name = data.getString("name")
                    val groupPrivateKey = data.getByteArray("groupPrivateKey")
                    val senderKeys = data.getString("senderKeys").split(" - ").map { ClosedGroupSenderKey.fromJSON(it)!! }
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    val admins = data.getString("admins").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.New(groupPublicKey, name, groupPrivateKey, senderKeys, members, admins)
                }
                "Info" -> {
                    val name = data.getString("name")
                    val senderKeys = data.getStringOrDefault("senderKeys", "").split(" - ").mapNotNull { ClosedGroupSenderKey.fromJSON(it) } // Can be empty
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    val admins = data.getString("admins").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.Info(groupPublicKey, name, senderKeys, members, admins)
                }
                "SenderKeyRequest" -> {
                    kind = Kind.SenderKeyRequest(groupPublicKey)
                }
                "SenderKey" -> {
                    val senderKey = ClosedGroupSenderKey.fromJSON(data.getString("senderKey"))!!
                    kind = Kind.SenderKey(groupPublicKey, senderKey)
                }
                else -> throw Exception("Invalid closed group update message kind: $rawKind.")
            }
            return ClosedGroupUpdateMessageSendJob(parameters, destination, kind)
        }
    }
}
