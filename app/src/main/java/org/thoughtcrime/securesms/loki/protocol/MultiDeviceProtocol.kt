package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.preferences.ProfileKeyUtil
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import java.security.SecureRandom
import java.util.*

object MultiDeviceProtocol {

    // TODO: refactor this to use new message sending job
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
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = Recipient.from(context, Address.fromSerialized(userPublicKey), false)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(),
                    true, false, false, Optional.absent())
            TextSecurePreferences.setLastConfigurationSyncTime(context, now)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
    }

    // TODO: refactor this to use new message sending job
    fun forceSyncConfigurationNowIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val contacts = ContactUtilities.getAllContacts(context).filter { recipient ->
            !recipient.isGroupRecipient && !recipient.isBlocked && !recipient.name.isNullOrEmpty() && !recipient.isLocalNumber && recipient.address.serialize().isNotEmpty()
        }.map { recipient ->
            ConfigurationMessage.Contact(recipient.address.serialize(), recipient.name!!, recipient.profileAvatar, recipient.profileKey)
        }
        val configurationMessage = ConfigurationMessage.getCurrent(contacts) ?: return
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = Recipient.from(context, Address.fromSerialized(userPublicKey), false)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(),
                    true, false, false, Optional.absent())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
    }

    // TODO: remove this after we migrate to new message receiving pipeline
    @JvmStatic
    fun handleConfigurationMessage(context: Context, content: SignalServiceProtos.Content, senderPublicKey: String, timestamp: Long) {
        if (TextSecurePreferences.getConfigurationMessageSynced(context) && !TextSecurePreferences.shouldUpdateProfile(context, timestamp)) return
        val configurationMessage = ConfigurationMessage.fromProto(content) ?: return
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        if (senderPublicKey != userPublicKey) return
        val storage = MessagingConfiguration.shared.storage
        val allClosedGroupPublicKeys = storage.getAllClosedGroupPublicKeys()

        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        val recipientDatabase = DatabaseFactory.getRecipientDatabase(context)

        val ourRecipient = Recipient.from(context, Address.fromSerialized(userPublicKey),false)

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
        if (configurationMessage.displayName.isNotEmpty()) {
            TextSecurePreferences.setProfileName(context, configurationMessage.displayName)
            recipientDatabase.setProfileName(ourRecipient, configurationMessage.displayName)
        }
        if (configurationMessage.profileKey.isNotEmpty()) {
            val profileKey = Base64.encodeBytes(configurationMessage.profileKey)
            ProfileKeyUtil.setEncodedProfileKey(context, profileKey)
            recipientDatabase.setProfileKey(ourRecipient, configurationMessage.profileKey)
            if (!configurationMessage.profilePicture.isNullOrEmpty() && TextSecurePreferences.getProfilePictureURL(context) != configurationMessage.profilePicture) {
                TextSecurePreferences.setProfilePictureURL(context, configurationMessage.profilePicture)
                TextSecurePreferences.setProfileAvatarId(context, SecureRandom().nextInt())
                ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(ourRecipient, configurationMessage.profilePicture))
            }
        }
        for (contact in configurationMessage.contacts) {
            val address = Address.fromSerialized(contact.publicKey)
            val recipient = Recipient.from(context, address, true)
            if (!contact.profilePicture.isNullOrEmpty()) {
                recipientDatabase.setProfileAvatar(recipient, contact.profilePicture)
            }
            if (contact.profileKey?.isNotEmpty() == true) {
                recipientDatabase.setProfileKey(recipient, contact.profileKey)
            }
            if (contact.name.isNotEmpty()) {
                recipientDatabase.setProfileName(recipient, contact.name)
            }
            recipientDatabase.setProfileSharing(recipient, true)
            // create Thread if needed
            threadDatabase.getOrCreateThreadIdFor(recipient)
        }
        if (configurationMessage.contacts.isNotEmpty()) {
            threadDatabase.notifyUpdatedFromConfig()
        }
        // TODO: handle new configuration message fields or handle in new pipeline
        TextSecurePreferences.setConfigurationMessageSynced(context, true)
        TextSecurePreferences.setLastProfileUpdateTime(context, timestamp)
    }
}