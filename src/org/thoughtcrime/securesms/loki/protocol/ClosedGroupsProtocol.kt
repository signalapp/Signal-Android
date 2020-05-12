package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol

object ClosedGroupsProtocol {

    @JvmStatic
    fun getDestinations(groupID: String, context: Context): List<Address> {
        if (GroupUtil.isRSSFeed(groupID)) { return listOf() }
        if (GroupUtil.isOpenGroup(groupID)) {
            val result = mutableListOf<Address>()
            result.add(Address.fromSerialized(groupID))
            return result
        } else {
            val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false)
            val recipients = members.flatMap { member ->
                MultiDeviceProtocol.shared.getAllLinkedDevices(member.address.serialize()).map { Address.fromSerialized(it) }
            }.toMutableSet()
            val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
            if (masterPublicKey != null && recipients.contains(Address.fromSerialized(masterPublicKey))) {
                recipients.remove(Address.fromSerialized(masterPublicKey))
            }
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            if (userPublicKey != null && recipients.contains(Address.fromSerialized(userPublicKey))) {
                recipients.remove(Address.fromSerialized(userPublicKey))
            }
            return recipients.toList()
        }
    }

    @JvmStatic
    fun leaveGroup(context: Context, recipient: Recipient): Boolean {
        if (!recipient.address.isClosedGroup) { return true }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val message = GroupUtil.createGroupLeaveMessage(context, recipient)
        if (threadID < 0 || !message.isPresent) { return false }
        MessageSender.send(context, message.get(), threadID, false, null)
        // Remove the *master* device from the group
        val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val publicKeyToUse = masterPublicKey ?: TextSecurePreferences.getLocalNumber(context)
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.remove(groupID, Address.fromSerialized(publicKeyToUse))
        return true
    }
}