package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

object ClosedGroupsProtocol {

    fun leaveGroup(context: Context, recipient: Recipient): Boolean {
        if (!recipient.address.isClosedGroup) {  return true }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val message = GroupUtil.createGroupLeaveMessage(context, recipient)
        if (threadID < 0 || !message.isPresent) { return false }
        MessageSender.send(context, message.get(), threadID, false, null)
        // Remove the *master* device from the group
        val masterHexPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val userPublicKey = masterHexPublicKey ?: TextSecurePreferences.getLocalNumber(context)
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.remove(groupID, Address.fromSerialized(userPublicKey))
        return true
    }
}