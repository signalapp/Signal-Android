package org.thoughtcrime.securesms.loki

import android.content.Context
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

object FriendRequestHandler {
  enum class ActionType { Sending, Sent, Failed }

  @JvmStatic
  fun updateFriendRequestState(context: Context, type: ActionType, messageId: Long, threadId: Long) {
    if (threadId < 0) return
    val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId) ?: return
    if (!recipient.address.isPhone) { return }

    val currentFriendStatus = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadId)
    // Update thread status if we haven't sent a friend request before
    if (currentFriendStatus != LokiThreadFriendRequestStatus.REQUEST_RECEIVED &&
            currentFriendStatus != LokiThreadFriendRequestStatus.REQUEST_SENT &&
            currentFriendStatus != LokiThreadFriendRequestStatus.FRIENDS
    ) {
      val threadFriendStatus = when (type) {
        ActionType.Sending -> LokiThreadFriendRequestStatus.REQUEST_SENDING
        ActionType.Failed -> LokiThreadFriendRequestStatus.NONE
        ActionType.Sent -> LokiThreadFriendRequestStatus.REQUEST_SENT
      }
      DatabaseFactory.getLokiThreadDatabase(context).setFriendRequestStatus(threadId, threadFriendStatus)
    }

    // Update message status
    if (messageId >= 0) {
      val messageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
      val friendRequestStatus = messageDatabase.getFriendRequestStatus(messageId)
      if (type == ActionType.Sending) {
        // We only want to update message status if we aren't friends with another of their devices
        // This avoids spam in the ui where it would keep telling the user that they sent a friend request on every single message
        isFriendsWithAnyLinkedDevice(context, recipient).successUi { isFriends ->
          if (!isFriends && friendRequestStatus == LokiMessageFriendRequestStatus.NONE) {
            messageDatabase.setFriendRequestStatus(messageId, LokiMessageFriendRequestStatus.REQUEST_SENDING)
          }
        }
      } else if (friendRequestStatus != LokiMessageFriendRequestStatus.NONE) {
        // Update the friend request status of the message if we have it
        val messageFriendRequestStatus = when (type) {
          ActionType.Failed -> LokiMessageFriendRequestStatus.REQUEST_FAILED
          ActionType.Sent -> LokiMessageFriendRequestStatus.REQUEST_PENDING
          else -> throw IllegalStateException()
        }
        messageDatabase.setFriendRequestStatus(messageId, messageFriendRequestStatus)
      }
    }
  }

  @JvmStatic
  fun updateLastFriendRequestMessage(context: Context, threadId: Long, status: LokiMessageFriendRequestStatus) {
    if (threadId < 0) { return }

    val messages = DatabaseFactory.getSmsDatabase(context).getAllMessageIDs(threadId)
    val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
    val lastMessage = messages.find {
      val friendRequestStatus = lokiMessageDatabase.getFriendRequestStatus(it)
      friendRequestStatus == LokiMessageFriendRequestStatus.REQUEST_PENDING
    } ?: return

    DatabaseFactory.getLokiMessageDatabase(context).setFriendRequestStatus(lastMessage, status)
  }

  @JvmStatic
  fun receivedIncomingFriendRequestMessage(context: Context, threadId: Long) {
    val smsMessageDatabase = DatabaseFactory.getSmsDatabase(context)

    // We only want to update the last message status if we're not friends with any of their linked devices
    // This ensures that we don't spam the UI with accept/decline messages
    val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId) ?: return
    if (!recipient.address.isPhone) { return }

    isFriendsWithAnyLinkedDevice(context, recipient).successUi { isFriends ->
      if (isFriends) { return@successUi }

      // Since messages are forwarded to the primary device thread, we need to update it there
      val messageCount = smsMessageDatabase.getMessageCountForThread(threadId)
      val messageID = smsMessageDatabase.getIDForMessageAtIndex(threadId, messageCount - 1) // The message that was just received
      if (messageID < 0) { return@successUi }

      val messageDatabase = DatabaseFactory.getLokiMessageDatabase(context)

      // We need to go through and set all messages which are REQUEST_PENDING to NONE
      smsMessageDatabase.getAllMessageIDs(threadId)
              .filter { messageDatabase.getFriendRequestStatus(it) == LokiMessageFriendRequestStatus.REQUEST_PENDING }
              .forEach {
                messageDatabase.setFriendRequestStatus(it, LokiMessageFriendRequestStatus.NONE)
              }

      // Set the last message to pending
      messageDatabase.setFriendRequestStatus(messageID, LokiMessageFriendRequestStatus.REQUEST_PENDING)
    }
  }
}