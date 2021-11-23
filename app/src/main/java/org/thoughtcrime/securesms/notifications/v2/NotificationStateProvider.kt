package org.thoughtcrime.securesms.notifications.v2

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MmsSmsColumns
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CursorUtil
import java.lang.IllegalStateException

/**
 * Queries the message databases to determine messages that should be in notifications.
 */
object NotificationStateProvider {

  private val TAG = Log.tag(NotificationStateProvider::class.java)

  @WorkerThread
  fun constructNotificationState(context: Context, stickyThreads: Map<Long, MessageNotifierV2.StickyThread>): NotificationStateV2 {
    val messages: MutableList<NotificationMessage> = mutableListOf()

    SignalDatabase.mmsSms.getMessagesForNotificationState(stickyThreads.values).use { unreadMessages ->
      if (unreadMessages.count == 0) {
        return NotificationStateV2.EMPTY
      }

      MmsSmsDatabase.readerFor(unreadMessages).use { reader ->
        var record: MessageRecord? = reader.next
        while (record != null) {
          val threadRecipient: Recipient? = SignalDatabase.threads.getRecipientForThreadId(record.threadId)
          if (threadRecipient != null) {
            val hasUnreadReactions = CursorUtil.requireInt(unreadMessages, MmsSmsColumns.REACTIONS_UNREAD) == 1

            messages += NotificationMessage(
              messageRecord = record,
              reactions = if (hasUnreadReactions) SignalDatabase.reactions.getReactions(MessageId(record.id, record.isMms)) else emptyList(),
              threadRecipient = threadRecipient,
              threadId = record.threadId,
              stickyThread = stickyThreads.containsKey(record.threadId),
              isUnreadMessage = CursorUtil.requireInt(unreadMessages, MmsSmsColumns.READ) == 0,
              hasUnreadReactions = hasUnreadReactions,
              lastReactionRead = CursorUtil.requireLong(unreadMessages, MmsSmsColumns.REACTIONS_LAST_SEEN)
            )
          }
          try {
            record = reader.next
          } catch (e: IllegalStateException) {
            // XXX Weird SQLCipher bug that's being investigated
            record = null
            Log.w(TAG, "Failed to read next record!", e)
          }
        }
      }
    }

    val conversations: MutableList<NotificationConversation> = mutableListOf()
    messages.groupBy { it.threadId }
      .forEach { (threadId, threadMessages) ->
        var notificationItems: MutableList<NotificationItemV2> = mutableListOf()

        for (notification: NotificationMessage in threadMessages) {
          if (notification.includeMessage()) {
            notificationItems.add(MessageNotification(notification.threadRecipient, notification.messageRecord))
          }

          if (notification.hasUnreadReactions) {
            notification.reactions.filter { notification.includeReaction(it) }
              .forEach { notificationItems.add(ReactionNotification(notification.threadRecipient, notification.messageRecord, it)) }
          }
        }

        notificationItems.sort()
        if (notificationItems.isNotEmpty() && stickyThreads.containsKey(threadId) && !notificationItems.last().individualRecipient.isSelf) {
          val indexOfOldestNonSelfMessage: Int = notificationItems.indexOfLast { it.individualRecipient.isSelf } + 1
          notificationItems = notificationItems.slice(indexOfOldestNonSelfMessage..notificationItems.lastIndex).toMutableList()
        }

        if (notificationItems.isNotEmpty()) {
          conversations += NotificationConversation(notificationItems[0].threadRecipient, threadId, notificationItems)
        }
      }

    return NotificationStateV2(conversations)
  }

  private data class NotificationMessage(
    val messageRecord: MessageRecord,
    val reactions: List<ReactionRecord>,
    val threadRecipient: Recipient,
    val threadId: Long,
    val stickyThread: Boolean,
    val isUnreadMessage: Boolean,
    val hasUnreadReactions: Boolean,
    val lastReactionRead: Long
  ) {
    private val isUnreadIncoming: Boolean = isUnreadMessage && !messageRecord.isOutgoing

    fun includeMessage(): Boolean {
      return (isUnreadIncoming || stickyThread) && (threadRecipient.isNotMuted || (threadRecipient.isAlwaysNotifyMentions && messageRecord.hasSelfMention()))
    }

    fun includeReaction(reaction: ReactionRecord): Boolean {
      return reaction.author != Recipient.self().id && messageRecord.isOutgoing && reaction.dateReceived > lastReactionRead && threadRecipient.isNotMuted
    }

    private val Recipient.isNotMuted: Boolean
      get() = !isMuted

    private val Recipient.isAlwaysNotifyMentions: Boolean
      get() = mentionSetting == RecipientDatabase.MentionSetting.ALWAYS_NOTIFY
  }
}
