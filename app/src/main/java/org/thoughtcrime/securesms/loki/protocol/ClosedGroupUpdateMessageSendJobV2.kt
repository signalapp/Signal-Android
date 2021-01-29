package org.thoughtcrime.securesms.loki.protocol

import com.google.protobuf.ByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.session.libsession.messaging.jobs.Data
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.meta.TTLUtilities
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.session.libsession.utilities.Hex

import java.util.*
import java.util.concurrent.TimeUnit

class ClosedGroupUpdateMessageSendJobV2 private constructor(parameters: Parameters, private val destination: String, private val kind: Kind) : BaseJob(parameters) {

    sealed class Kind {
        class New(val publicKey: ByteArray, val name: String, val encryptionKeyPair: ECKeyPair, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class Update(val name: String, val members: Collection<ByteArray>) : Kind()
        class EncryptionKeyPair(val wrappers: Collection<KeyPairWrapper>) : Kind() // The new encryption key pair encrypted for each member individually
    }

    companion object {
        const val KEY = "ClosedGroupUpdateMessageSendJobV2"
    }

    @Serializable
    data class KeyPairWrapper(val publicKey: String, val encryptedKeyPair: ByteArray) {

        companion object {

            fun fromProto(proto: SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toString(), proto.encryptedKeyPair.toByteArray())
            }
        }

        fun toProto(): SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper {
            val result = SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey))
            result.encryptedKeyPair = ByteString.copyFrom(encryptedKeyPair)
            return result.build()
        }
    }

    constructor(destination: String, kind: Kind) : this(Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(20)
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
                builder.putByteArray("publicKey", kind.publicKey)
                builder.putString("name", kind.name)
                builder.putByteArray("encryptionKeyPairPublicKey", kind.encryptionKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
                builder.putByteArray("encryptionKeyPairPrivateKey", kind.encryptionKeyPair.privateKey.serialize())
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
                val admins = kind.admins.joinToString(" - ") { it.toHexString() }
                builder.putString("admins", admins)
            }
            is Kind.Update -> {
                builder.putString("kind", "Update")
                builder.putString("name", kind.name)
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
            }
            is Kind.EncryptionKeyPair -> {
                builder.putString("kind", "EncryptionKeyPair")
                val wrappers = kind.wrappers.joinToString(" - ") { Json.encodeToString(it) }
                builder.putString("wrappers", wrappers)
            }
        }
        return builder.build()
    }

    class Factory : Job.Factory<ClosedGroupUpdateMessageSendJobV2> {

        override fun create(parameters: Parameters, data: Data): ClosedGroupUpdateMessageSendJobV2 {
            val destination = data.getString("destination")
            val rawKind = data.getString("kind")
            val kind: Kind
            when (rawKind) {
                "New" -> {
                    val publicKey = data.getByteArray("publicKey")
                    val name = data.getString("name")
                    val encryptionKeyPairPublicKey = data.getByteArray("encryptionKeyPairPublicKey")
                    val encryptionKeyPairPrivateKey = data.getByteArray("encryptionKeyPairPrivateKey")
                    val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairPublicKey), DjbECPrivateKey(encryptionKeyPairPrivateKey))
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    val admins = data.getString("admins").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.New(publicKey, name, encryptionKeyPair, members, admins)
                }
                "Update" -> {
                    val name = data.getString("name")
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.Update(name, members)
                }
                "EncryptionKeyPair" -> {
                    val wrappers: Collection<KeyPairWrapper> = data.getString("wrappers").split(" - ").map { Json.decodeFromString(it) }
                    kind = Kind.EncryptionKeyPair(wrappers)
                }
                else -> throw Exception("Invalid closed group update message kind: $rawKind.")
            }
            return ClosedGroupUpdateMessageSendJobV2(parameters, destination, kind)
        }
    }

    public override fun onRun() {
        val contentMessage = SignalServiceProtos.Content.newBuilder()
        val dataMessage = SignalServiceProtos.DataMessage.newBuilder()
        val closedGroupUpdate = SignalServiceProtos.ClosedGroupUpdateV2.newBuilder()
        when (kind) {
            is Kind.New -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW
                closedGroupUpdate.publicKey = ByteString.copyFrom(kind.publicKey)
                closedGroupUpdate.name = kind.name
                val encryptionKeyPair = SignalServiceProtos.ClosedGroupUpdateV2.KeyPair.newBuilder()
                encryptionKeyPair.publicKey = ByteString.copyFrom(kind.encryptionKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
                encryptionKeyPair.privateKey = ByteString.copyFrom(kind.encryptionKeyPair.privateKey.serialize())
                closedGroupUpdate.encryptionKeyPair =  encryptionKeyPair.build()
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
            }
            is Kind.Update -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.UPDATE
                closedGroupUpdate.name = kind.name
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
            }
            is Kind.EncryptionKeyPair -> {
                closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR
                closedGroupUpdate.addAllWrappers(kind.wrappers.map { it.toProto() })
            }
        }
        dataMessage.closedGroupUpdateV2 = closedGroupUpdate.build()
        contentMessage.dataMessage = dataMessage.build()
        val serializedContentMessage = contentMessage.build().toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(destination)
        val recipient = recipient(context, destination)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        val ttl: Int
        when (kind) {
            is Kind.EncryptionKeyPair -> ttl = 4 * 24 * 60 * 60 * 1000
            else -> ttl = TTLUtilities.getTTL(TTLUtilities.MessageType.ClosedGroupUpdate)
        }
        try {
            // isClosedGroup can always be false as it's only used in the context of legacy closed groups
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedContentMessage, false, ttl, false,
                    true, false, false, false)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send closed group update message to: $destination due to error: $e.")
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        return true
    }

    override fun onCanceled() { }
}