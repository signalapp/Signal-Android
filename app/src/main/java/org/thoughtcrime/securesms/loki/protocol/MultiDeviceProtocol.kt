package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.loki.utilities.recipient
import java.util.*

object MultiDeviceProtocol {

    // TODO: refactor this to use new message sending job
    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val lastSyncTime = TextSecurePreferences.getLastConfigurationSyncTime(context)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 2 * 24 * 60 * 60 * 1000) return
        val configurationMessage = ConfigurationMessage.getCurrent()
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = recipient(context, userPublicKey)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(), false,
                    true, false, true, Optional.absent())
            TextSecurePreferences.setLastConfigurationSyncTime(context, now)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
    }

    // TODO: refactor this to use new message sending job
    fun forceSyncConfigurationNowIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return
        val configurationMessage = ConfigurationMessage.getCurrent()
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = recipient(context, userPublicKey)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(), false,
                    true, false, true, Optional.absent())
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
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

            val closedGroupUpdate = SignalServiceProtos.ClosedGroupUpdateV2.newBuilder()
            closedGroupUpdate.type = SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW
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