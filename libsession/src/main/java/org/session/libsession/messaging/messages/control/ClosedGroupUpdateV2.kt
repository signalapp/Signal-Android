package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos

class ClosedGroupUpdateV2() : ControlMessage() {

    override val ttl: Long = run {
        when (kind) {
            is Kind.EncryptionKeyPair -> return@run 4 * 24 * 60 * 60 * 1000
            else -> return@run 2 * 24 * 60 * 60 * 1000
        }
    }

    var kind: Kind? = null

    // Kind enum
    sealed class Kind {
        class New(val publicKey: ByteString, val name: String, val encryptionKeyPair: ECKeyPair, val members: List<ByteString>, val admins: List<ByteString>) : Kind()
        class Update(val name: String, val members: List<ByteString>) : Kind()
        class EncryptionKeyPair(val wrappers: Collection<KeyPairWrapper>) : Kind()
    }

    companion object {
        const val TAG = "ClosedGroupUpdateV2"

        fun fromProto(proto: SignalServiceProtos.Content): ClosedGroupUpdateV2? {
            val closedGroupUpdateProto = proto.dataMessage?.closedGroupUpdateV2 ?: return null
            val kind: Kind
            when(closedGroupUpdateProto.type) {
                SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW -> {
                    val publicKey = closedGroupUpdateProto.publicKey ?: return null
                    val name = closedGroupUpdateProto.name ?: return null
                    val encryptionKeyPairAsProto = closedGroupUpdateProto.encryptionKeyPair ?: return null

                    try {
                        val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray()), DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
                        kind = Kind.New(publicKey, name, encryptionKeyPair, closedGroupUpdateProto.membersList, closedGroupUpdateProto.adminsList)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't parse key pair")
                        return null
                    }
                }
                SignalServiceProtos.ClosedGroupUpdateV2.Type.UPDATE -> {
                    val name = closedGroupUpdateProto.name ?: return null
                    kind = Kind.Update(name, closedGroupUpdateProto.membersList)
                }
                SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR -> {
                    val wrappers = closedGroupUpdateProto.wrappersList.mapNotNull { KeyPairWrapper.fromProto(it) }
                    kind = Kind.EncryptionKeyPair(wrappers)
                }
            }
            return ClosedGroupUpdateV2(kind)
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
                !kind.publicKey.isEmpty && kind.name.isNotEmpty() && kind.encryptionKeyPair.publicKey != null && kind.encryptionKeyPair.privateKey != null && kind.members.isNotEmpty() && kind.admins.isNotEmpty()
            }
            is Kind.Update -> {
                kind.name.isNotEmpty()
            }
            is Kind.EncryptionKeyPair -> {
                true
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
            val closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2.Builder = SignalServiceProtos.ClosedGroupUpdateV2.newBuilder()
            when (kind) {
                is Kind.New -> {
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW
                    closedGroupUpdate.publicKey = kind.publicKey
                    closedGroupUpdate.name = kind.name
                    val encryptionKeyPairAsProto = SignalServiceProtos.ClosedGroupUpdateV2.KeyPair.newBuilder()
                    encryptionKeyPairAsProto.publicKey = ByteString.copyFrom(kind.encryptionKeyPair.publicKey.serialize())
                    encryptionKeyPairAsProto.privateKey = ByteString.copyFrom(kind.encryptionKeyPair.privateKey.serialize())

                    try {
                        closedGroupUpdate.encryptionKeyPair = encryptionKeyPairAsProto.build()
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't construct closed group update proto from: $this")
                        return null
                    }
                    closedGroupUpdate.addAllMembers(kind.members)
                    closedGroupUpdate.addAllAdmins(kind.admins)
                }
                is Kind.Update -> {
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.UPDATE
                    closedGroupUpdate.name = kind.name
                    closedGroupUpdate.addAllMembers(kind.members)
                }
                is Kind.EncryptionKeyPair -> {
                    closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR
                    closedGroupUpdate.addAllWrappers(kind.wrappers.map { it.toProto() })
                }
            }
            val contentProto = SignalServiceProtos.Content.newBuilder()
            val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
            dataMessageProto.closedGroupUpdateV2 = closedGroupUpdate.build()
            // Group context
            contentProto.dataMessage = dataMessageProto.build()
            return contentProto.build()
        } catch (e: Exception) {
            Log.w(ClosedGroupUpdate.TAG, "Couldn't construct closed group update proto from: $this")
            return null
        }
    }

    class KeyPairWrapper(val publicKey: String?, private val encryptedKeyPair: ByteString?) {
        companion object {
            fun fromProto(proto: SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toString(), proto.encryptedKeyPair)
            }
        }

        fun toProto(): SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper? {
            val publicKey = publicKey ?: return null
            val encryptedKeyPair = encryptedKeyPair ?: return null
            val result = SignalServiceProtos.ClosedGroupUpdateV2.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(publicKey.toByteArray())
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