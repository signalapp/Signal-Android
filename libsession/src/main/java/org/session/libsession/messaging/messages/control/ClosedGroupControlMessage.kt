package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage
import org.session.libsignal.service.loki.utilities.toHexString
import org.session.libsignal.utilities.Hex

class ClosedGroupControlMessage() : ControlMessage() {

    override val ttl: Long = run {
        when (kind) {
            is Kind.EncryptionKeyPair -> return@run 4 * 24 * 60 * 60 * 1000
            else -> return@run 2 * 24 * 60 * 60 * 1000
        }
    }

    override val isSelfSendValid: Boolean = true

    var kind: Kind? = null

    // Kind enum
    sealed class Kind {
        class New(val publicKey: ByteString, val name: String, val encryptionKeyPair: ECKeyPair, val members: List<ByteString>, val admins: List<ByteString>) : Kind()
        /// - Note: Deprecated in favor of more explicit group updates.
        class Update(val name: String, val members: List<ByteString>) : Kind()
        /// An encryption key pair encrypted for each member individually.
        ///
        /// - Note: `publicKey` is only set when an encryption key pair is sent in a one-to-one context (i.e. not in a group).
        class EncryptionKeyPair(val publicKey: ByteString?, val wrappers: Collection<KeyPairWrapper>) : Kind()
        class NameChange(val name: String) : Kind()
        class MembersAdded(val members: List<ByteString>) : Kind()
        class MembersRemoved( val members: List<ByteString>) : Kind()
        class MemberLeft : Kind()
        class EncryptionKeyPairRequest: Kind()

        val description: String = run {
            when(this) {
                is New -> "new"
                is Update -> "update"
                is EncryptionKeyPair -> "encryptionKeyPair"
                is NameChange -> "nameChange"
                is MembersAdded -> "membersAdded"
                is MembersRemoved -> "membersRemoved"
                is MemberLeft -> "memberLeft"
                is EncryptionKeyPairRequest -> "encryptionKeyPairRequest"
            }
        }
    }

    companion object {
        const val TAG = "ClosedGroupControlMessage"

        fun fromProto(proto: SignalServiceProtos.Content): ClosedGroupControlMessage? {
            val closedGroupControlMessageProto = proto.dataMessage?.closedGroupControlMessage ?: return null
            val kind: Kind
            when(closedGroupControlMessageProto.type) {
                DataMessage.ClosedGroupControlMessage.Type.NEW -> {
                    val publicKey = closedGroupControlMessageProto.publicKey ?: return null
                    val name = closedGroupControlMessageProto.name ?: return null
                    val encryptionKeyPairAsProto = closedGroupControlMessageProto.encryptionKeyPair ?: return null

                    try {
                        val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray()), DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
                        kind = Kind.New(publicKey, name, encryptionKeyPair, closedGroupControlMessageProto.membersList, closedGroupControlMessageProto.adminsList)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't parse key pair")
                        return null
                    }
                }
                DataMessage.ClosedGroupControlMessage.Type.UPDATE -> {
                    val name = closedGroupControlMessageProto.name ?: return null
                    kind = Kind.Update(name, closedGroupControlMessageProto.membersList)
                }
                DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR -> {
                    val publicKey = closedGroupControlMessageProto.publicKey
                    val wrappers = closedGroupControlMessageProto.wrappersList.mapNotNull { KeyPairWrapper.fromProto(it) }
                    kind = Kind.EncryptionKeyPair(publicKey, wrappers)
                }
                DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE -> {
                    val name = closedGroupControlMessageProto.name ?: return null
                    kind = Kind.NameChange(name)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED -> {
                    kind = Kind.MembersAdded(closedGroupControlMessageProto.membersList)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED -> {
                    kind = Kind.MembersRemoved(closedGroupControlMessageProto.membersList)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT -> {
                    kind = Kind.MemberLeft()
                }
                DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR_REQUEST -> {
                    kind = Kind.EncryptionKeyPairRequest()
                }
            }
            return ClosedGroupControlMessage(kind)
        }
    }

    // constructor
    internal constructor(kind: Kind?) : this() {
        this.kind = kind
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        val kind = kind ?: return false
        return when(kind) {
            is Kind.New -> {
                !kind.publicKey.isEmpty && kind.name.isNotEmpty() && kind.encryptionKeyPair.publicKey != null
                        && kind.encryptionKeyPair.privateKey != null && kind.members.isNotEmpty() && kind.admins.isNotEmpty()
            }
            is Kind.Update -> kind.name.isNotEmpty()
            is Kind.EncryptionKeyPair -> true
            is Kind.NameChange -> kind.name.isNotEmpty()
            is Kind.MembersAdded -> kind.members.isNotEmpty()
            is Kind.MembersRemoved -> kind.members.isNotEmpty()
            is Kind.MemberLeft -> true
            is Kind.EncryptionKeyPairRequest -> true
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val kind = kind
        if (kind == null) {
            Log.w(TAG, "Couldn't construct closed group update proto from: $this")
            return null
        }
        try {
            val closedGroupControlMessage: DataMessage.ClosedGroupControlMessage.Builder = DataMessage.ClosedGroupControlMessage.newBuilder()
            when (kind) {
                is Kind.New -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.NEW
                    closedGroupControlMessage.publicKey = kind.publicKey
                    closedGroupControlMessage.name = kind.name
                    val encryptionKeyPairAsProto = SignalServiceProtos.KeyPair.newBuilder()
                    encryptionKeyPairAsProto.publicKey = ByteString.copyFrom(kind.encryptionKeyPair.publicKey.serialize())
                    encryptionKeyPairAsProto.privateKey = ByteString.copyFrom(kind.encryptionKeyPair.privateKey.serialize())

                    try {
                        closedGroupControlMessage.encryptionKeyPair = encryptionKeyPairAsProto.build()
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't construct closed group update proto from: $this")
                        return null
                    }
                    closedGroupControlMessage.addAllMembers(kind.members)
                    closedGroupControlMessage.addAllAdmins(kind.admins)
                }
                is Kind.Update -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.UPDATE
                    closedGroupControlMessage.name = kind.name
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.EncryptionKeyPair -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR
                    closedGroupControlMessage.publicKey = kind.publicKey
                    closedGroupControlMessage.addAllWrappers(kind.wrappers.map { it.toProto() })
                }
                is Kind.NameChange -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE
                    closedGroupControlMessage.name = kind.name
                }
                is Kind.MembersAdded -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MembersRemoved -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MemberLeft -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT
                }
                is Kind.EncryptionKeyPairRequest -> {
                    // TODO: closedGroupControlMessage.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR_REQUEST
                }
            }
            val contentProto = SignalServiceProtos.Content.newBuilder()
            val dataMessageProto = DataMessage.newBuilder()
            dataMessageProto.closedGroupControlMessage = closedGroupControlMessage.build()
            // Group context
            contentProto.dataMessage = dataMessageProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct closed group update proto from: $this")
            return null
        }
    }

    class KeyPairWrapper(val publicKey: String?, val encryptedKeyPair: ByteString?) {

        val isValid: Boolean = run {
            this.publicKey != null && this.encryptedKeyPair != null
        }

        companion object {
            fun fromProto(proto: DataMessage.ClosedGroupControlMessage.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toByteArray().toHexString(), proto.encryptedKeyPair)
            }
        }

        fun toProto(): DataMessage.ClosedGroupControlMessage.KeyPairWrapper? {
            val publicKey = publicKey ?: return null
            val encryptedKeyPair = encryptedKeyPair ?: return null
            val result = DataMessage.ClosedGroupControlMessage.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey))
            result.encryptedKeyPair = encryptedKeyPair

            return try {
                result.build()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't construct key pair wrapper proto from: $this")
                return null
            }
        }
    }
}