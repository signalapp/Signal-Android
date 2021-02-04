package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey

class ClosedGroupUpdate() : ControlMessage() {

    var kind: Kind? = null

    // Kind enum
    sealed class Kind {
        class New(val groupPublicKey: ByteArray, val name: String, val groupPrivateKey: ByteArray, val senderKeys: Collection<org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey>, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class Info(val groupPublicKey: ByteArray, val name: String, val senderKeys: Collection<org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey>, val members: Collection<ByteArray>, val admins: Collection<ByteArray>) : Kind()
        class SenderKeyRequest(val groupPublicKey: ByteArray) : Kind()
        class SenderKey(val groupPublicKey: ByteArray, val senderKey: org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey) : Kind()
    }

    companion object {
        const val TAG = "ClosedGroupUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ClosedGroupUpdate? {
            val closedGroupUpdateProto = proto.dataMessage?.closedGroupUpdate ?: return null
            val groupPublicKey = closedGroupUpdateProto.groupPublicKey
            var kind: Kind
            when(closedGroupUpdateProto.type) {
                SignalServiceProtos.ClosedGroupUpdate.Type.NEW -> {
                    val name = closedGroupUpdateProto.name ?: return null
                    val groupPrivateKey = closedGroupUpdateProto.groupPrivateKey ?: return null
                    val senderKeys = closedGroupUpdateProto.senderKeysList.map { ClosedGroupSenderKey.fromProto(it) }
                    kind = Kind.New(
                            groupPublicKey = groupPublicKey.toByteArray(),
                            name = name,
                            groupPrivateKey = groupPrivateKey.toByteArray(),
                            senderKeys = senderKeys,
                            members = closedGroupUpdateProto.membersList.map { it.toByteArray() },
                            admins = closedGroupUpdateProto.adminsList.map { it.toByteArray() }
                    )
                }
                SignalServiceProtos.ClosedGroupUpdate.Type.INFO -> {
                    val name = closedGroupUpdateProto.name ?: return null
                    val senderKeys = closedGroupUpdateProto.senderKeysList.map { ClosedGroupSenderKey.fromProto(it) }
                    kind = Kind.Info(
                            groupPublicKey = groupPublicKey.toByteArray(),
                            name = name,
                            senderKeys = senderKeys,
                            members = closedGroupUpdateProto.membersList.map { it.toByteArray() },
                            admins = closedGroupUpdateProto.adminsList.map { it.toByteArray() }
                    )
                }
                SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY_REQUEST -> {
                    kind = Kind.SenderKeyRequest(groupPublicKey = groupPublicKey.toByteArray())
                }
                SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY -> {
                    val senderKeyProto = closedGroupUpdateProto.senderKeysList?.first() ?: return null
                    kind = Kind.SenderKey(
                            groupPublicKey = groupPublicKey.toByteArray(),
                            senderKey = ClosedGroupSenderKey.fromProto(senderKeyProto)
                    )
                }
            }
            return ClosedGroupUpdate(kind)
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
        when(kind) {
            is Kind.New -> {
                return !kind.groupPublicKey.isEmpty() && !kind.name.isEmpty() && !kind.groupPrivateKey.isEmpty() && !kind.members.isEmpty() && !kind.admins.isEmpty()
            }
            is Kind.Info -> {
                return !kind.groupPublicKey.isEmpty() && !kind.name.isEmpty() && !kind.members.isEmpty() && !kind.admins.isEmpty()
            }
            is Kind.SenderKeyRequest -> {
                return !kind.groupPublicKey.isEmpty()
            }
            is Kind.SenderKey -> {
                return !kind.groupPublicKey.isEmpty()
            }
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val kind = kind
        if (kind == null) {
            Log.w(TAG, "Couldn't construct closed group update proto from: $this")
            return null
        }
        try {
            val closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdate.Builder = SignalServiceProtos.ClosedGroupUpdate.newBuilder()
            when (kind) {
                is Kind.New -> {
                    closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.NEW
                    closedGroupUpdate.name = kind.name
                    closedGroupUpdate.groupPrivateKey = ByteString.copyFrom(kind.groupPrivateKey)
                    closedGroupUpdate.addAllSenderKeys(kind.senderKeys.map { it.toProto() })
                    closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                    closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
                }
                is Kind.Info -> {
                    closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.INFO
                    closedGroupUpdate.name = kind.name
                    closedGroupUpdate.addAllSenderKeys(kind.senderKeys.map { it.toProto() })
                    closedGroupUpdate.addAllMembers(kind.members.map { ByteString.copyFrom(it) })
                    closedGroupUpdate.addAllAdmins(kind.admins.map { ByteString.copyFrom(it) })
                }
                is Kind.SenderKeyRequest -> {
                    closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY_REQUEST
                }
                is Kind.SenderKey -> {
                    closedGroupUpdate.groupPublicKey = ByteString.copyFrom(kind.groupPublicKey)
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdate.Type.SENDER_KEY
                    closedGroupUpdate.addAllSenderKeys(listOf( kind.senderKey.toProto() ))
                }
            }
            val contentProto = SignalServiceProtos.Content.newBuilder()
            val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
            dataMessageProto.closedGroupUpdate = closedGroupUpdate.build()
            contentProto.dataMessage = dataMessageProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct closed group update proto from: $this")
            return null
        }
        return null
    }

}

// extension functions to class ClosedGroupSenderKey

private fun ClosedGroupSenderKey.Companion.fromProto(proto: SignalServiceProtos.ClosedGroupUpdate.SenderKey): ClosedGroupSenderKey {
    return ClosedGroupSenderKey(chainKey = proto.chainKey.toByteArray(), keyIndex = proto.keyIndex, publicKey = proto.publicKey.toByteArray())
}

private fun ClosedGroupSenderKey.toProto(): SignalServiceProtos.ClosedGroupUpdate.SenderKey {
    val proto = SignalServiceProtos.ClosedGroupUpdate.SenderKey.newBuilder()
    proto.chainKey = ByteString.copyFrom(chainKey)
    proto.keyIndex = keyIndex
    proto.publicKey = ByteString.copyFrom(publicKey)
    return proto.build()
}