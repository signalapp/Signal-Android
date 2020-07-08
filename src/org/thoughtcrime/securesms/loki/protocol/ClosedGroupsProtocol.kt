package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.loki.utilities.timeout
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiAPI
import org.whispersystems.signalservice.loki.api.fileserver.LokiFileServerAPI
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import java.util.*

object ClosedGroupsProtocol {

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun shouldIgnoreContentMessage(context: Context, conversation: Recipient, groupID: String?, content: SignalServiceContent): Boolean {
        if (!conversation.address.isClosedGroup || groupID == null) { return false }
        // A closed group's members should never include slave devices
        val senderPublicKey = content.sender
        LokiFileServerAPI.shared.getDeviceLinks(senderPublicKey).timeout(6000).get()
        val senderMasterPublicKey = MultiDeviceProtocol.shared.getMasterDevice(senderPublicKey)
        val publicKeyToCheckFor = senderMasterPublicKey ?: senderPublicKey
        val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, true)
        return !members.contains(recipient(context, publicKeyToCheckFor))
    }

    @JvmStatic
    fun shouldIgnoreGroupCreatedMessage(context: Context, group: SignalServiceGroup): Boolean {
        val members = group.members
        val masterPublicKeyOrNull = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val masterPublicKey = masterPublicKeyOrNull ?: TextSecurePreferences.getLocalNumber(context)
        return !members.isPresent || !members.get().contains(masterPublicKey)
    }

    @JvmStatic
    fun getDestinations(groupID: String, context: Context): Promise<List<Address>, Exception> {
        if (GroupUtil.isRSSFeed(groupID)) { return Promise.of(listOf()) }
        if (GroupUtil.isOpenGroup(groupID)) {
            val result = mutableListOf<Address>()
            result.add(Address.fromSerialized(groupID))
            return Promise.of(result)
        } else {
            // A closed group's members should never include slave devices
            val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false)
            return LokiFileServerAPI.shared.getDeviceLinks(members.map { it.address.serialize() }.toSet()).map {
                val result = members.flatMap { member ->
                    MultiDeviceProtocol.shared.getAllLinkedDevices(member.address.serialize()).map { Address.fromSerialized(it) }
                }.toMutableSet()
                val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
                if (userMasterPublicKey != null && result.contains(Address.fromSerialized(userMasterPublicKey))) {
                    result.remove(Address.fromSerialized(userMasterPublicKey))
                }
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)
                if (userPublicKey != null && result.contains(Address.fromSerialized(userPublicKey))) {
                    result.remove(Address.fromSerialized(userPublicKey))
                }
                result.toList()
            }
        }
    }

    @JvmStatic
    fun leaveGroup(context: Context, recipient: Recipient): Boolean {
        if (!recipient.address.isClosedGroup) { return true }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val message = GroupUtil.createGroupLeaveMessage(context, recipient)
        if (threadID < 0 || !message.isPresent) { return false }
        MessageSender.send(context, message.get(), threadID, false, null)
        // Remove the master device from the group (a closed group's members should never include slave devices)
        val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val publicKeyToRemove = masterPublicKey ?: TextSecurePreferences.getLocalNumber(context)
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.remove(groupID, Address.fromSerialized(publicKeyToRemove))
        return true
    }

    @JvmStatic
    fun establishSessionsWithMembersIfNeeded(context: Context, members: List<String>) {
        // A closed group's members should never include slave devices
        val allDevices = members.flatMap { member ->
            MultiDeviceProtocol.shared.getAllLinkedDevices(member)
        }.toMutableSet()
        val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        if (userMasterPublicKey != null && allDevices.contains(userMasterPublicKey)) {
            allDevices.remove(userMasterPublicKey)
        }
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        if (userPublicKey != null && allDevices.contains(userPublicKey)) {
            allDevices.remove(userPublicKey)
        }
        for (device in allDevices) {
            val deviceAsAddress = SignalProtocolAddress(device, SignalServiceAddress.DEFAULT_DEVICE_ID)
            val hasSession = TextSecureSessionStore(context).containsSession(deviceAsAddress)
            if (hasSession) { continue }
            if (DatabaseFactory.getLokiAPIDatabase(context).getSessionRequestTimestamp(device) != null) { return }
            DatabaseFactory.getLokiAPIDatabase(context).setSessionRequestTimestamp(device, Date().time)
            val sessionRequest = EphemeralMessage.createSessionRequest(device)
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(sessionRequest))
        }
    }
}