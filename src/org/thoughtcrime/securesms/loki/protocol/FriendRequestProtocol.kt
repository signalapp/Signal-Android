package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiMessageFriendRequestStatus
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object FriendRequestProtocol {

    fun getLastMessageID(context: Context, threadID: Long): Long? {
        val db = DatabaseFactory.getSmsDatabase(context)
        val messageCount = db.getMessageCountForThread(threadID)
        if (messageCount == 0) { return null }
        return db.getIDForMessageAtIndex(threadID, messageCount - 1)
    }

    @JvmStatic
    fun handleFriendRequestAcceptanceIfNeeded(context: Context, publicKey: String, content: SignalServiceContent) {
        // If we get an envelope that isn't a friend request, then we can infer that we had to use
        // Signal cipher decryption and thus that we have a session with the other person.
        if (content.isFriendRequest) { return }
        val recipient = recipient(context, publicKey)
        // Friend requests don't apply to groups
        if (recipient.isGroupRecipient) { return }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = lokiThreadDB.getFriendRequestStatus(threadID)
        // Guard against invalid state transitions
        if (threadFRStatus != LokiThreadFriendRequestStatus.REQUEST_SENDING && threadFRStatus != LokiThreadFriendRequestStatus.REQUEST_SENT
            && threadFRStatus != LokiThreadFriendRequestStatus.REQUEST_RECEIVED) { return }
        lokiThreadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS)
        val lastMessageID = getLastMessageID(context, threadID)
        if (lastMessageID != null) {
            DatabaseFactory.getLokiMessageDatabase(context).setFriendRequestStatus(lastMessageID, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED)
        }
        // Send a contact sync message if needed
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val allUserDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey)
        if (allUserDevices.contains(publicKey)) { return }
        val deviceToSync = MultiDeviceProtocol.shared.getMasterDevice(publicKey) ?: publicKey
        SyncMessagesProtocol.syncContact(context, Address.fromSerialized(deviceToSync))
    }

    private fun canFriendRequestBeAutoAccepted(context: Context, publicKey: String): Boolean {
        val recipient = recipient(context, publicKey)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = lokiThreadDB.getFriendRequestStatus(threadID)
        if (threadFRStatus == LokiThreadFriendRequestStatus.REQUEST_SENT) {
            // This can happen if Alice sent Bob a friend request, Bob declined, but then Bob changed his
            // mind and sent a friend request to Alice. In this case we want Alice to auto-accept the request
            // and send a friend request accepted message back to Bob. We don't check that sending the
            // friend request accepted message succeeds. Even if it doesn't, the thread's current friend
            // request status will be set to FRIENDS for Alice making it possible for Alice to send messages
            // to Bob. When Bob receives a message, his thread's friend request status will then be set to
            // FRIENDS. If we do check for a successful send before updating Alice's thread's friend request
            // status to FRIENDS, we can end up in a deadlock where both users' threads' friend request statuses
            // are SENT.
            return true
        }
        // Auto-accept any friend requests from the user's own linked devices
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val allUserDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey)
        if (allUserDevices.contains(publicKey)) { return true }
        // Auto-accept if the user is friends with any of the sender's linked devices.
        val allSenderDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(publicKey)
        if (allSenderDevices.any { device ->
            val deviceAsRecipient = recipient(context, publicKey)
            val deviceThreadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(deviceAsRecipient)
            lokiThreadDB.getFriendRequestStatus(deviceThreadID) == LokiThreadFriendRequestStatus.FRIENDS
        }) {
            return true
        }
        return false
    }

    @JvmStatic
    fun handleFriendRequestMessageIfNeeded(context: Context, publicKey: String, content: SignalServiceContent) {
        if (!content.isFriendRequest) { return }
        val recipient = recipient(context, publicKey)
        // Friend requests don't apply to groups
        if (recipient.isGroupRecipient) { return }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = lokiThreadDB.getFriendRequestStatus(threadID)
        if (canFriendRequestBeAutoAccepted(context, publicKey)) {
            lokiThreadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS)
            val lastMessageID = getLastMessageID(context, threadID)
            if (lastMessageID != null) {
                DatabaseFactory.getLokiMessageDatabase(context).setFriendRequestStatus(lastMessageID, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED)
            }
            val ephemeralMessage = EphemeralMessage.create(publicKey)
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
        } else if (threadFRStatus != LokiThreadFriendRequestStatus.FRIENDS) {
            // Checking that the sender of the message isn't already a friend is necessary because otherwise
            // the following situation can occur: Alice and Bob are friends. Bob loses his database and his
            // friend request status is reset to NONE. Bob now sends Alice a friend request. Alice's thread's
            // friend request status is reset to RECEIVED
            lokiThreadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.REQUEST_RECEIVED)
            val lastMessageID = getLastMessageID(context, threadID)
            if (lastMessageID != null) {
                DatabaseFactory.getLokiMessageDatabase(context).setFriendRequestStatus(lastMessageID, LokiMessageFriendRequestStatus.REQUEST_PENDING)
            }
        }
    }

    @JvmStatic
    fun isFriendRequestFromBeforeRestoration(context: Context, content: SignalServiceContent): Boolean {
        return content.isFriendRequest && content.timestamp < TextSecurePreferences.getRestorationTime(context)
    }

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

    @JvmStatic
    fun setFriendRequestStatusToSentIfNeeded(context: Context, messageID: Long, threadID: Long) {
        val messageDB = DatabaseFactory.getLokiMessageDatabase(context)
        val messageFRStatus = messageDB.getFriendRequestStatus(messageID)
        if (messageFRStatus == LokiMessageFriendRequestStatus.NONE || messageFRStatus == LokiMessageFriendRequestStatus.REQUEST_EXPIRED
            || messageFRStatus == LokiMessageFriendRequestStatus.REQUEST_SENDING) {
            messageDB.setFriendRequestStatus(messageID, LokiMessageFriendRequestStatus.REQUEST_PENDING)
        }
        val threadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = threadDB.getFriendRequestStatus(threadID)
        if (threadFRStatus == LokiThreadFriendRequestStatus.NONE || threadFRStatus == LokiThreadFriendRequestStatus.REQUEST_EXPIRED
            || threadFRStatus == LokiThreadFriendRequestStatus.REQUEST_SENDING) {
            threadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.REQUEST_SENT)
        }
    }

    @JvmStatic
    fun setFriendRequestStatusToFailedIfNeeded(context: Context, messageID: Long, threadID: Long) {
        val messageDB = DatabaseFactory.getLokiMessageDatabase(context)
        val messageFRStatus = messageDB.getFriendRequestStatus(messageID)
        if (messageFRStatus == LokiMessageFriendRequestStatus.REQUEST_SENDING) {
            messageDB.setFriendRequestStatus(messageID, LokiMessageFriendRequestStatus.REQUEST_FAILED)
        }
        val threadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = threadDB.getFriendRequestStatus(threadID)
        if (threadFRStatus == LokiThreadFriendRequestStatus.REQUEST_SENDING) {
            threadDB.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.NONE)
        }
    }
}