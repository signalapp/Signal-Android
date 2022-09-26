package org.thoughtcrime.securesms.notifications.v2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.signal.core.util.PendingIntentFlags
import org.thoughtcrime.securesms.notifications.DeleteNotificationReceiver
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Hold all state for notifications for all conversations.
 */
data class NotificationState(val conversations: List<NotificationConversation>, val muteFilteredMessages: List<FilteredMessage>, val profileFilteredMessages: List<FilteredMessage>) {

  val threadCount: Int = conversations.size
  val isEmpty: Boolean = conversations.isEmpty()

  val messageCount: Int by lazy {
    conversations.fold(0) { messageCount, conversation ->
      messageCount + conversation.messageCount
    }
  }

  val notificationItems: List<NotificationItem> by lazy {
    conversations.map { it.notificationItems }
      .flatten()
      .sorted()
  }

  val notificationIds: Set<Int> by lazy {
    conversations.map { it.notificationId }
      .toSet()
  }

  val mostRecentNotification: NotificationItem?
    get() = notificationItems.lastOrNull()

  val mostRecentSender: Recipient?
    get() = mostRecentNotification?.individualRecipient

  fun getNonVisibleConversation(visibleThread: ConversationId?): List<NotificationConversation> {
    return conversations.filterNot { it.thread == visibleThread }
  }

  fun getConversation(conversationId: ConversationId): NotificationConversation? {
    return conversations.firstOrNull { it.thread == conversationId }
  }

  fun getDeleteIntent(context: Context): PendingIntent? {
    val ids = LongArray(messageCount)
    val mms = BooleanArray(ids.size)
    val threads: MutableList<ConversationId> = mutableListOf()

    conversations.forEach { conversation ->
      threads += conversation.thread
      conversation.notificationItems.forEachIndexed { index, notificationItem ->
        ids[index] = notificationItem.id
        mms[index] = notificationItem.isMms
      }
    }

    val intent = Intent(context, DeleteNotificationReceiver::class.java)
      .setAction(DeleteNotificationReceiver.DELETE_NOTIFICATION_ACTION)
      .putExtra(DeleteNotificationReceiver.EXTRA_IDS, ids)
      .putExtra(DeleteNotificationReceiver.EXTRA_MMS, mms)
      .putParcelableArrayListExtra(DeleteNotificationReceiver.EXTRA_THREADS, ArrayList(threads))
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getBroadcast(context, 0, intent, PendingIntentFlags.updateCurrent())
  }

  fun getMarkAsReadIntent(context: Context): PendingIntent? {
    val intent = Intent(context, MarkReadReceiver::class.java).setAction(MarkReadReceiver.CLEAR_ACTION)
      .putParcelableArrayListExtra(MarkReadReceiver.THREADS_EXTRA, ArrayList(conversations.map { it.thread }))
      .putExtra(MarkReadReceiver.NOTIFICATION_ID_EXTRA, NotificationIds.MESSAGE_SUMMARY)
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getBroadcast(context, 0, intent, PendingIntentFlags.updateCurrent())
  }

  fun getThreadsWithMostRecentNotificationFromSelf(): Set<ConversationId> {
    return conversations.filter { it.mostRecentNotification.individualRecipient.isSelf }
      .map { it.thread }
      .toSet()
  }

  data class FilteredMessage(val id: Long, val isMms: Boolean)

  companion object {
    val EMPTY = NotificationState(emptyList(), emptyList(), emptyList())
  }
}
