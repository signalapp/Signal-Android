package org.thoughtcrime.securesms.loki

import android.content.Context
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.successUi
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus
import java.lang.IllegalStateException

object FriendRequestHandler {
  enum class ActionType { Sending, Sent, Failed }

  @JvmStatic
  fun handleFriendRequest(context: Context, type: ActionType, messageId: Long, threadId: Long) {
    // Update thread status
    // Note: Do we need to only update these if we're not friends?
    if (threadId >= 0) {
      val threadFriendStatus = when (type) {
        ActionType.Sending -> LokiThreadFriendRequestStatus.REQUEST_SENDING
        ActionType.Failed -> LokiThreadFriendRequestStatus.NONE
        ActionType.Sent -> LokiThreadFriendRequestStatus.REQUEST_SENT
      }
      DatabaseFactory.getLokiThreadDatabase(context).setFriendRequestStatus(threadId, threadFriendStatus)
    }

    // Update message status
    val recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId)
    if (recipient != null && messageId >= 0) {
      val messageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
      val messageFriendRequestStatus = messageDatabase.getFriendRequestStatus(messageId)
      if (type == ActionType.Sending && messageFriendRequestStatus == LokiMessageFriendRequestStatus.NONE) {
        // We only want to update message status if we aren't friends with another of their devices
        // This avoids spam in the ui where it would keep telling the user that they sent a friend request on every single message
        if (!isFriendsWithAnyLinkedDevice(context, recipient)) {
          messageDatabase.setFriendRequestStatus(messageId, LokiMessageFriendRequestStatus.REQUEST_SENDING)
        }
      } else if (messageFriendRequestStatus != LokiMessageFriendRequestStatus.NONE) {
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
}