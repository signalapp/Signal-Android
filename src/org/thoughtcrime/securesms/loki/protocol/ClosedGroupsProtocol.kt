package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
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
import org.whispersystems.signalservice.loki.api.SnodeAPI
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol
import java.util.*

object ClosedGroupsProtocol {

    @JvmStatic
    fun shouldIgnoreContentMessage(context: Context, address: Address, groupID: String?, senderPublicKey: String): Boolean {
        if (!address.isClosedGroup || groupID == null) { return false }
        /*
        FileServerAPI.shared.getDeviceLinks(senderPublicKey).timeout(6000).get()
        val senderMasterPublicKey = MultiDeviceProtocol.shared.getMasterDevice(senderPublicKey)
        val publicKeyToCheckFor = senderMasterPublicKey ?: senderPublicKey
         */
        val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, true)
        return !members.contains(recipient(context, senderPublicKey))
    }

    @JvmStatic
    fun getMessageDestinations(context: Context, groupID: String): List<Address> {
        if (GroupUtil.isRSSFeed(groupID)) { return listOf() }
        if (GroupUtil.isOpenGroup(groupID)) {
            return listOf( Address.fromSerialized(groupID) )
        } else {
            // TODO: Shared sender keys
            return DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false).map { it.address }
            /*
            return FileServerAPI.shared.getDeviceLinks(members.map { it.address.serialize() }.toSet()).map {
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
             */
        }
    }

    @JvmStatic
    fun leaveLegacyGroup(context: Context, recipient: Recipient): Boolean {
        if (!recipient.address.isClosedGroup) { return true }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val message = GroupUtil.createGroupLeaveMessage(context, recipient).orNull()
        if (threadID < 0 || message == null) { return false }
        MessageSender.send(context, message, threadID, false, null)
        /*
        val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val publicKeyToRemove = masterPublicKey ?: TextSecurePreferences.getLocalNumber(context)
         */
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.remove(groupID, Address.fromSerialized(userPublicKey))
        return true
    }

    @JvmStatic
    fun establishSessionsWithMembersIfNeeded(context: Context, members: List<String>) {
        @Suppress("NAME_SHADOWING") val members = members.toMutableSet()
        /*
        val allDevices = members.flatMap { member ->
            MultiDeviceProtocol.shared.getAllLinkedDevices(member)
        }.toMutableSet()
        val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        if (userMasterPublicKey != null && allDevices.contains(userMasterPublicKey)) {
            allDevices.remove(userMasterPublicKey)
        }
         */
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        if (userPublicKey != null && members.contains(userPublicKey)) {
            members.remove(userPublicKey)
        }
        for (member in members) {
            ApplicationContext.getInstance(context).sendSessionRequestIfNeeded(member)
        }
    }
}