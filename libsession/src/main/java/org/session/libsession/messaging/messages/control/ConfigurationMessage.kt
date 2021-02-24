package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.preferences.ProfileKeyUtil
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.session.libsignal.utilities.Hex

class ConfigurationMessage(val closedGroups: List<ClosedGroup>, val openGroups: List<String>, val displayName: String, val profilePicture: String?, val profileKey: ByteArray): ControlMessage() {

    class ClosedGroup(val publicKey: String, val name: String, val encryptionKeyPair: ECKeyPair, val members: List<String>, val admins: List<String>) {
        val isValid: Boolean get() = members.isNotEmpty() && admins.isNotEmpty()

        override fun toString(): String {
            return name
        }

        companion object {
            fun fromProto(proto: SignalServiceProtos.ConfigurationMessage.ClosedGroup): ClosedGroup? {
                if (!proto.hasPublicKey() || !proto.hasName() || !proto.hasEncryptionKeyPair()) return null
                val publicKey = proto.publicKey.toByteArray().toHexString()
                val name = proto.name
                val encryptionKeyPairAsProto = proto.encryptionKeyPair
                val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray().removing05PrefixIfNeeded()),
                                                  DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
                val members = proto.membersList.map { it.toByteArray().toHexString() }
                val admins = proto.adminsList.map { it.toByteArray().toHexString() }
                return ClosedGroup(publicKey, name, encryptionKeyPair, members, admins)
            }
        }

        fun toProto(): SignalServiceProtos.ConfigurationMessage.ClosedGroup? {
            val result = SignalServiceProtos.ConfigurationMessage.ClosedGroup.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey))
            result.name = name
            val encryptionKeyPairAsProto = SignalServiceProtos.KeyPair.newBuilder()
            encryptionKeyPairAsProto.publicKey = ByteString.copyFrom(encryptionKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
            encryptionKeyPairAsProto.privateKey = ByteString.copyFrom(encryptionKeyPair.privateKey.serialize())
            result.encryptionKeyPair = encryptionKeyPairAsProto.build()
            result.addAllMembers(members.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })
            result.addAllAdmins(admins.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })
            return result.build()
        }
    }

    override val ttl: Long = 4 * 24 * 60 * 60 * 1000
    override val isSelfSendValid: Boolean = true

    companion object {

        fun getCurrent(): ConfigurationMessage {
            val closedGroups = mutableListOf<ClosedGroup>()
            val openGroups = mutableListOf<String>()
            val sharedConfig = MessagingConfiguration.shared
            val storage = sharedConfig.storage
            val context = sharedConfig.context
            val displayName = TextSecurePreferences.getProfileName(context)!!
            val profilePicture = TextSecurePreferences.getProfilePictureURL(context)
            val profileKey = ProfileKeyUtil.getProfileKey(context)
            val groups = storage.getAllGroups()
            for (groupRecord in groups) {
                if (groupRecord.isClosedGroup) {
                    if (!groupRecord.members.contains(Address.fromSerialized(storage.getUserPublicKey()!!))) continue
                    val groupPublicKey = GroupUtil.getDecodedGroupIDAsData(groupRecord.encodedId).toHexString()
                    if (!storage.isClosedGroup(groupPublicKey)) continue
                    val encryptionKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: continue
                    val closedGroup = ClosedGroup(groupPublicKey, groupRecord.title, encryptionKeyPair, groupRecord.members.map { it.serialize() }, groupRecord.admins.map { it.serialize() })
                    closedGroups.add(closedGroup)
                }
                if (groupRecord.isOpenGroup) {
                    val threadID = storage.getThreadID(groupRecord.encodedId) ?: continue
                    val openGroup = storage.getOpenGroup(threadID) ?: continue
                    openGroups.add(openGroup.server)
                }
            }

            return ConfigurationMessage(closedGroups, openGroups, displayName, profilePicture, profileKey)
        }

        fun fromProto(proto: SignalServiceProtos.Content): ConfigurationMessage? {
            if (!proto.hasConfigurationMessage()) return null
            val configurationProto = proto.configurationMessage
            val closedGroups = configurationProto.closedGroupsList.mapNotNull { ClosedGroup.fromProto(it) }
            val openGroups = configurationProto.openGroupsList
            val displayName = configurationProto.displayName
            val profilePicture = configurationProto.profilePicture
            val profileKey = configurationProto.profileKey
            return ConfigurationMessage(closedGroups, openGroups, displayName, profilePicture, profileKey.toByteArray())
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val configurationProto = SignalServiceProtos.ConfigurationMessage.newBuilder()
        configurationProto.addAllClosedGroups(closedGroups.mapNotNull { it.toProto() })
        configurationProto.addAllOpenGroups(openGroups)
        configurationProto.displayName = displayName
        configurationProto.profilePicture = profilePicture
        configurationProto.profileKey = ByteString.copyFrom(profileKey)
        val contentProto = SignalServiceProtos.Content.newBuilder()
        contentProto.configurationMessage = configurationProto.build()
        return contentProto.build()
    }

    override fun toString(): String {
        return """ 
            ConfigurationMessage(
                closedGroups: ${(closedGroups)}
                openGroups: ${(openGroups)}
                displayName: $displayName
                profilePicture: $profilePicture
                profileKey: $profileKey
            )
        """.trimIndent()
    }
}