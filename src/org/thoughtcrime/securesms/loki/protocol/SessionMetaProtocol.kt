package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object SessionMetaProtocol {

    @JvmStatic
    fun sendEphemeralMessage(context: Context, publicKey: String) {
        val ephemeralMessage = EphemeralMessage.create(publicKey)
        ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun canUserReplyToNotification(recipient: Recipient, context: Context): Boolean {
        val isGroup = recipient.isGroupRecipient
        if (isGroup) { return !recipient.address.isRSSFeed }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun shouldSendReadReceipt(address: Address, context: Context): Boolean {
        if (address.isGroup) { return false }
        val recipient = Recipient.from(context, address,false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun shouldSendTypingIndicator(recipient: Recipient, context: Context): Boolean {
        if (recipient.isGroupRecipient) { return false }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }
}