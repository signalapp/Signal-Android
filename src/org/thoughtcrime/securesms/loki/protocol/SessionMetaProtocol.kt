package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object SessionMetaProtocol {

    @JvmStatic
    fun canUserReplyToNotification(recipient: Recipient, context: Context): Boolean {
        val isGroup = recipient.isGroupRecipient
        if (isGroup) { return !recipient.address.isRSSFeed }
        val linkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(recipient.address.serialize())
        return linkedDevices.any { device ->
            val recipient = Recipient.from(context, Address.fromSerialized(device), false)
            val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
            DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
        }
    }

    @JvmStatic
    fun shouldSendReadReceipt(publicKey: String, context: Context): Boolean {
        val recipient = Recipient.from(context, Address.fromSerialized(publicKey),false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }

    @JvmStatic
    fun shouldSendTypingIndicator(recipient: Recipient, context: Context): Boolean {
        if (recipient.isGroupRecipient) { return false }
        val recipient = Recipient.from(context, Address.fromSerialized(recipient.address.serialize()),false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }
}