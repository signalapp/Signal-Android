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
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage
import org.session.libsignal.service.loki.utilities.TTLUtilities
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.session.libsignal.utilities.Hex

import java.util.concurrent.TimeUnit

class ClosedGroupUpdateMessageSendJobV2 private constructor(parameters: Parameters,
                                                            private val destination: String,
                                                            private val kind: Kind,
                                                            private val sentTime: Long) : BaseJob(parameters) {

    sealed class Kind {
        class New(val publicKey: ByteArray, val name: String, val encryptionKeyPair: ECKeyPair, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class Update(val name: String, val members: Collection<ByteArray>) : Kind()
        object Leave : Kind()
        class RemoveMembers(val members: Collection<ByteArray>) : Kind()
        class AddMembers(val members: Collection<ByteArray>) : Kind()
        class NameChange(val name: String) : Kind()
        class EncryptionKeyPair(val wrappers: Collection<KeyPairWrapper>, val targetUser: String?) : Kind() // The new encryption key pair encrypted for each member individually
    }

    companion object {
        const val KEY = "ClosedGroupUpdateMessageSendJobV2"
    }

    @Serializable
    data class KeyPairWrapper(val publicKey: String, val encryptedKeyPair: ByteArray) {

        companion object {

            fun fromProto(proto: DataMessage.ClosedGroupControlMessage.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toString(), proto.encryptedKeyPair.toByteArray())
            }
        }

        fun toProto(): DataMessage.ClosedGroupControlMessage.KeyPairWrapper {
            val result = DataMessage.ClosedGroupControlMessage.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey))
            result.encryptedKeyPair = ByteString.copyFrom(encryptedKeyPair)
            return result.build()
        }
    }

    constructor(destination: String, kind: Kind, sentTime: Long) : this(Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(20)
            .build(),
            destination,
            kind,
            sentTime)

    override fun getFactoryKey(): String { return KEY }

    override fun serialize(): Data {
        val builder = Data.Builder()
        builder.putString("destination", destination)
        builder.putLong("sentTime", sentTime)
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
            is Kind.RemoveMembers -> {
                builder.putString("kind", "RemoveMembers")
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
            }
            Kind.Leave -> {
                builder.putString("kind", "Leave")
            }
            is Kind.AddMembers -> {
                builder.putString("kind", "AddMembers")
                val members = kind.members.joinToString(" - ") { it.toHexString() }
                builder.putString("members", members)
            }
            is Kind.NameChange -> {
                builder.putString("kind", "NameChange")
                builder.putString("name", kind.name)
            }
            is Kind.EncryptionKeyPair -> {
                builder.putString("kind", "EncryptionKeyPair")
                val wrappers = kind.wrappers.joinToString(" - ") { Json.encodeToString(it) }
                builder.putString("wrappers", wrappers)
                builder.putString("targetUser", kind.targetUser)
            }
        }
        return builder.build()
    }

    class Factory : Job.Factory<ClosedGroupUpdateMessageSendJobV2> {

        override fun create(parameters: Parameters, data: Data): ClosedGroupUpdateMessageSendJobV2 {
            val destination = data.getString("destination")
            val rawKind = data.getString("kind")
            val sentTime = data.getLong("sentTime")
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
                    val targetUser = data.getString("targetUser")
                    kind = Kind.EncryptionKeyPair(wrappers, targetUser)
                }
                "RemoveMembers" -> {
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.RemoveMembers(members)
                }
                "AddMembers" -> {
                    val members = data.getString("members").split(" - ").map { Hex.fromStringCondensed(it) }
                    kind = Kind.AddMembers(members)
                }
                "NameChange" -> {
                    val name = data.getString("name")
                    kind = Kind.NameChange(name)
                }
                "Leave" -> {
                    kind = Kind.Leave
                }
                else -> throw Exception("Invalid closed group update message kind: $rawKind.")
            }
            return ClosedGroupUpdateMessageSendJobV2(parameters, destination, kind, sentTime)
        }
    }

    public override fun onRun() {
        val sendDestination = if (kind is Kind.EncryptionKeyPair && kind.targetUser != null) {
            kind.targetUser
        } else {
            destination
        }
        val contentMessage = SignalServiceProtos.Content.newBuilder()
        val dataMessage = DataMessage.newBuilder()
        val closedGroupUpdate = DataMessage.ClosedGroupControlMessage.newBuilder()
        when (kind) {
            is Kind.New -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.NEW
                closedGroupUpdate.publicKey = ByteString.copyFrom(kind.publicKey)
                closedGroupUpdate.name = kind.name
                val encryptionKeyPair = SignalServiceProtos.KeyPair.newBuilder()
                encryptionKeyPair.publicKey = ByteString.copyFrom(kind.encryptionKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
                encryptionKeyPair.privateKey = ByteString.copyFrom(kind.encryptionKeyPair.privateKey.serialize())
                closedGroupUpdate.encryptionKeyPair =  encryptionKeyPair.build()
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
            }
            is Kind.Update -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.UPDATE
                closedGroupUpdate.name = kind.name
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
            }
            is Kind.EncryptionKeyPair -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR
                closedGroupUpdate.addAllWrappers(kind.wrappers.map { it.toProto() })
                if (kind.targetUser != null) {
                    closedGroupUpdate.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(destination))
                }
            }
            Kind.Leave -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT
            }
            is Kind.RemoveMembers -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
            }
            is Kind.AddMembers -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED
                closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
            }
            is Kind.NameChange -> {
                closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE
                closedGroupUpdate.name = kind.name
            }
        }
        dataMessage.closedGroupControlMessage = closedGroupUpdate.build()
        contentMessage.dataMessage = dataMessage.build()
        val serializedContentMessage = contentMessage.build().toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(sendDestination)
        val recipient = recipient(context, sendDestination)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        val ttl = when (kind) {
            is Kind.EncryptionKeyPair -> 4 * 24 * 60 * 60 * 1000
            else -> TTLUtilities.getTTL(TTLUtilities.MessageType.ClosedGroupUpdate)
        }
        try {
            // isClosedGroup can always be false as it's only used in the context of legacy closed groups
            messageSender.sendMessage(0, address, udAccess,
                    sentTime, serializedContentMessage, false, ttl,
                    true, false, false, Optional.absent())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send closed group update message to: $sendDestination due to error: $e.")
        }
    }

    public override fun onShouldRetry(e: Exception): Boolean {
        return true
    }

    override fun onCanceled() { }
}