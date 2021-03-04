package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.Hex
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities

object MultiDeviceProtocol {

    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val lastSyncTime = TextSecurePreferences.getLastConfigurationSyncTime(context)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 2 * 24 * 60 * 60 * 1000) return
        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
            !recipient.isBlocked && !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
        }.map { recipient ->
            ConfigurationMessage.Contact(recipient.address.serialize(), recipient.name!!, recipient.profileAvatar, recipient.profileKey)
        }
        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return
        MessageSender.send(configurationMessage, Address.fromSerialized(userPublicKey))
        TextSecurePreferences.setLastConfigurationSyncTime(context, now)
    }

    fun forceSyncConfigurationNowIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
            !recipient.isGroupRecipient && !recipient.isBlocked && !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
        }.map { recipient ->
            ConfigurationMessage.Contact(recipient.address.serialize(), recipient.name!!, recipient.profileAvatar, recipient.profileKey)
        }
        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return
        MessageSender.send(configurationMessage, Destination.from(Address.fromSerialized(userPublicKey)))
    }

    // TODO: remove this after we migrate to new message receiving pipeline
    @JvmStatic
    fun handleConfigurationMessage(context: Context, content: SignalServiceProtos.Content, senderPublicKey: String, timestamp: Long) {
        if (TextSecurePreferences.getConfigurationMessageSynced(context)) return
        val configurationMessage = ConfigurationMessage.fromProto(content) ?: return
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        if (senderPublicKey != userPublicKey) return
        val storage = MessagingConfiguration.shared.storage
        val allClosedGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
        for (closedGroup in configurationMessage.closedGroups) {
            if (allClosedGroupPublicKeys.contains(closedGroup.publicKey)) continue

            val closedGroupUpdate = DataMessage.ClosedGroupControlMessage.newBuilder()
            closedGroupUpdate.type = DataMessage.ClosedGroupControlMessage.Type.NEW
            closedGroupUpdate.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(closedGroup.publicKey))
            closedGroupUpdate.name = closedGroup.name
            val encryptionKeyPair = SignalServiceProtos.KeyPair.newBuilder()
            encryptionKeyPair.publicKey = ByteString.copyFrom(closedGroup.encryptionKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
            encryptionKeyPair.privateKey = ByteString.copyFrom(closedGroup.encryptionKeyPair.privateKey.serialize())
            closedGroupUpdate.encryptionKeyPair =  encryptionKeyPair.build()
            closedGroupUpdate.addAllMembers(closedGroup.members.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })
            closedGroupUpdate.addAllAdmins(closedGroup.admins.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) })

            ClosedGroupsProtocolV2.handleNewClosedGroup(context, closedGroupUpdate.build(), userPublicKey, timestamp)
        }
        val allOpenGroups = storage.getAllOpenGroups().map { it.value.server }
        for (openGroup in configurationMessage.openGroups) {
            if (allOpenGroups.contains(openGroup)) continue
            OpenGroupUtilities.addGroup(context, openGroup, 1)
        }
        // TODO: handle new configuration message fields or handle in new pipeline
        TextSecurePreferences.setConfigurationMessageSynced(context, true)
    }
}