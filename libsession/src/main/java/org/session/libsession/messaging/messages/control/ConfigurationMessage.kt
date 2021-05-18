package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.preferences.ProfileKeyUtil
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.Hex

class ConfigurationMessage(var closedGroups: List<ClosedGroup>, var openGroups: List<String>, var contacts: List<Contact>,
    var displayName: String, var profilePicture: String?, var profileKey: ByteArray) : ControlMessage() {

    override val isSelfSendValid: Boolean = true

    class ClosedGroup(var publicKey: String, var name: String, var encryptionKeyPair: ECKeyPair?, var members: List<String>, var admins: List<String>) {
        val isValid: Boolean get() = members.isNotEmpty() && admins.isNotEmpty()

        internal constructor() : this("", "", null, listOf(), listOf())

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
            encryptionKeyPairAsProto.publicKey = ByteString.copyFrom(encryptionKeyPair!!.publicKey.serialize().removing05PrefixIfNeeded())
            encryptionKeyPairAsProto.privateKey = ByteString.copyFrom(encryptionKeyPair!!.privateKey.serialize())
            result.encryptionKeyPair = encryptionKeyPairAsProto.build()
            result.addAllMembers(members.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })
            result.addAllAdmins(admins.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })
            return result.build()
        }
    }

    class Contact(var publicKey: String, var name: String, var profilePicture: String?, var profileKey: ByteArray?) {

        internal constructor() : this("", "", null, null)

        companion object {

            fun fromProto(proto: SignalServiceProtos.ConfigurationMessage.Contact): Contact? {
                if (!proto.hasName() || !proto.hasProfileKey()) return null
                val publicKey = proto.publicKey.toByteArray().toHexString()
                val name = proto.name
                val profilePicture = if (proto.hasProfilePicture()) proto.profilePicture else null
                val profileKey = if (proto.hasProfileKey()) proto.profileKey.toByteArray() else null
                return Contact(publicKey, name, profilePicture, profileKey)
            }
        }

        fun toProto(): SignalServiceProtos.ConfigurationMessage.Contact? {
            val result = SignalServiceProtos.ConfigurationMessage.Contact.newBuilder()
            result.name = this.name
            try {
                result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey))
            } catch (e: Exception) {
                return null
            }
            val profilePicture = profilePicture
            if (!profilePicture.isNullOrEmpty()) {
                result.profilePicture = profilePicture
            }
            val profileKey = profileKey
            if (profileKey != null) {
                result.profileKey = ByteString.copyFrom(profileKey)
            }
            return result.build()
        }
    }

    companion object {

        fun getCurrent(contacts: List<Contact>): ConfigurationMessage? {
            val closedGroups = mutableListOf<ClosedGroup>()
            val openGroups = mutableListOf<String>()
            val sharedConfig = MessagingModuleConfiguration.shared
            val storage = sharedConfig.storage
            val context = sharedConfig.context
            val displayName = TextSecurePreferences.getProfileName(context) ?: return null
            val profilePicture = TextSecurePreferences.getProfilePictureURL(context)
            val profileKey = ProfileKeyUtil.getProfileKey(context)
            val groups = storage.getAllGroups()
            for (group in groups) {
                if (group.isClosedGroup) {
                    if (!group.members.contains(Address.fromSerialized(storage.getUserPublicKey()!!))) continue
                    val groupPublicKey = GroupUtil.doubleDecodeGroupID(group.encodedId).toHexString()
                    val encryptionKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: continue
                    val closedGroup = ClosedGroup(groupPublicKey, group.title, encryptionKeyPair, group.members.map { it.serialize() }, group.admins.map { it.serialize() })
                    closedGroups.add(closedGroup)
                }
                if (group.isOpenGroup) {
                    val threadID = storage.getThreadID(group.encodedId) ?: continue
                    val openGroup = storage.getOpenGroup(threadID)
                    val openGroupV2 = storage.getV2OpenGroup(threadID)
                    val shareUrl = openGroup?.server ?: openGroupV2?.joinURL ?: continue
                    openGroups.add(shareUrl)
                }
            }
            return ConfigurationMessage(closedGroups, openGroups, contacts, displayName, profilePicture, profileKey)
        }

        fun fromProto(proto: SignalServiceProtos.Content): ConfigurationMessage? {
            if (!proto.hasConfigurationMessage()) return null
            val configurationProto = proto.configurationMessage
            val closedGroups = configurationProto.closedGroupsList.mapNotNull { ClosedGroup.fromProto(it) }
            val openGroups = configurationProto.openGroupsList
            val displayName = configurationProto.displayName
            val profilePicture = configurationProto.profilePicture
            val profileKey = configurationProto.profileKey
            val contacts = configurationProto.contactsList.mapNotNull { Contact.fromProto(it) }
            return ConfigurationMessage(closedGroups, openGroups, contacts, displayName, profilePicture, profileKey.toByteArray())
        }
    }

    internal constructor(): this(listOf(), listOf(), listOf(), "", null, byteArrayOf())

    override fun toProto(): SignalServiceProtos.Content? {
        val configurationProto = SignalServiceProtos.ConfigurationMessage.newBuilder()
        configurationProto.addAllClosedGroups(closedGroups.mapNotNull { it.toProto() })
        configurationProto.addAllOpenGroups(openGroups)
        configurationProto.addAllContacts(this.contacts.mapNotNull { it.toProto() })
        configurationProto.displayName = displayName
        val profilePicture = profilePicture
        if (!profilePicture.isNullOrEmpty()) {
            configurationProto.profilePicture = profilePicture
        }
        configurationProto.profileKey = ByteString.copyFrom(profileKey)
        val contentProto = SignalServiceProtos.Content.newBuilder()
        contentProto.configurationMessage = configurationProto.build()
        return contentProto.build()
    }

    override fun toString(): String {
        return """ 
            ConfigurationMessage(
                closedGroups: ${(closedGroups)},
                openGroups: ${(openGroups)},
                displayName: $displayName,
                profilePicture: $profilePicture,
                profileKey: $profileKey
            )
        """.trimIndent()
    }
}