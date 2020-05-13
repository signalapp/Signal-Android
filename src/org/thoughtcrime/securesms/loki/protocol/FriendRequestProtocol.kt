package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.protocol.todo.LokiMessageFriendRequestStatus
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object FriendRequestProtocol {

    @JvmStatic
    fun shouldUpdateFriendRequestStatusFromOutgoingTextMessage(context: Context, message: OutgoingTextMessage): Boolean {
        // The order of these checks matters
        if (message.recipient.isGroupRecipient) { return false }
        if (message.recipient.address.serialize() == TextSecurePreferences.getLocalNumber(context)) { return false }
        // TODO: Return false if the message is a device linking request
        // TODO: Return false if the message is a session request
        return message.isFriendRequest
    }

    @JvmStatic
    fun shouldUpdateFriendRequestStatusFromOutgoingMediaMessage(context: Context, message: OutgoingMediaMessage): Boolean {
        // The order of these checks matters
        if (message.recipient.isGroupRecipient) { return false }
        if (message.recipient.address.serialize() == TextSecurePreferences.getLocalNumber(context)) { return false }
        // TODO: Return false if the message is a device linking request
        // TODO: Return false if the message is a session request
        return message.isFriendRequest
    }

    @JvmStatic
    fun setFriendRequestStatusToSendingIfNeeded(context: Context, messageID: Long, threadID: Long) {
        val messageDB = DatabaseFactory.getLokiMessageDatabase(context)
        val messageFRStatus = messageDB.getFriendRequestStatus(messageID)
        if (messageFRStatus == LokiMessageFriendRequestStatus.NONE || messageFRStatus == LokiMessageFriendRequestStatus.REQUEST_EXPIRED) {
            messageDB.setFriendRequestStatus(messageID, LokiMessageFriendRequestStatus.REQUEST_SENDING)
        }
        val threadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = threadDB.getFriendRequestStatus(threadID)
        if (threadFRStatus == LokiThreadFriendRequestStatus.NONE || threadFRStatus == LokiThreadFriendRequestStatus.REQUEST_EXPIRED) {
            threadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.REQUEST_SENDING)
        }
    }
}