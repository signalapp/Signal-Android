package org.thoughtcrime.securesms.notifications.v2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import androidx.core.app.TaskStackBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.TurnOffContactJoinedNotificationsActivity
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DeleteNotificationReceiver
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.notifications.RemoteReplyReceiver
import org.thoughtcrime.securesms.notifications.ReplyMethod
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.Util

/**
 * Encapsulate all the notifications for a given conversation (thread) and the top
 * level information about said conversation.
 */
data class NotificationConversation(
  val recipient: Recipient,
  val threadId: Long,
  val notificationItems: List<NotificationItemV2>
) {
  val mostRecentNotification: NotificationItemV2 = notificationItems.last()
  val notificationId: Int = NotificationIds.getNotificationIdForThread(threadId)
  val sortKey: Long = Long.MAX_VALUE - mostRecentNotification.timestamp
  val messageCount: Int = notificationItems.size
  val isGroup: Boolean = recipient.isGroup
  val isOnlyContactJoinedEvent: Boolean = messageCount == 1 && mostRecentNotification.isJoined

  fun getContentTitle(context: Context): CharSequence {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact) {
      recipient.getDisplayName(context)
    } else {
      context.getString(R.string.SingleRecipientNotificationBuilder_signal)
    }
  }

  fun getContactLargeIcon(context: Context): Drawable? {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact) {
      recipient.getContactDrawable(context)
    } else {
      GeneratedContactPhoto("Unknown", R.drawable.ic_profile_outline_40).asDrawable(context, AvatarColor.UNKNOWN)
    }
  }

  fun getSlideBigPictureUri(context: Context): Uri? {
    return if (notificationItems.size == 1 && SignalStore.settings().messageNotificationsPrivacy.isDisplayMessage && !KeyCachingService.isLocked(context)) {
      mostRecentNotification.getBigPictureUri()
    } else {
      null
    }
  }

  fun getContentText(context: Context): CharSequence? {
    val privacy: NotificationPrivacyPreference = SignalStore.settings().messageNotificationsPrivacy
    val stringBuilder = SpannableStringBuilder()

    if (privacy.isDisplayContact && recipient.isGroup) {
      stringBuilder.append(Util.getBoldedString(mostRecentNotification.individualRecipient.getDisplayName(context) + ": "))
    }

    return if (privacy.isDisplayMessage) {
      stringBuilder.append(mostRecentNotification.getPrimaryText(context))
    } else {
      stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message))
    }
  }

  fun getConversationTitle(context: Context): CharSequence? {
    if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact) {
      return if (isGroup) recipient.getDisplayName(context) else null
    }
    return context.getString(R.string.SingleRecipientNotificationBuilder_signal)
  }

  fun getWhen(): Long {
    return mostRecentNotification.timestamp
  }

  fun hasNewNotifications(): Boolean {
    return notificationItems.any { it.isNewNotification }
  }

  fun getChannelId(context: Context): String {
    return if (isOnlyContactJoinedEvent) {
      NotificationChannels.JOIN_EVENTS
    } else {
      recipient.notificationChannel ?: NotificationChannels.getMessagesChannel(context)
    }
  }

  fun hasSameContent(other: NotificationConversation?): Boolean {
    if (other == null) {
      return false
    }

    return messageCount == other.messageCount && notificationItems.zip(other.notificationItems).all { (item, otherItem) -> item.hasSameContent(otherItem) }
  }

  fun getPendingIntent(context: Context): PendingIntent {
    val intent: Intent = ConversationIntents.createBuilder(context, recipient.id, threadId)
      .withStartingPosition(mostRecentNotification.getStartingPosition(context))
      .build()
      .makeUniqueToPreventMerging()

    return TaskStackBuilder.create(context)
      .addNextIntentWithParentStack(intent)
      .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
  }

  fun getDeleteIntent(context: Context): PendingIntent? {
    val ids = LongArray(notificationItems.size)
    val mms = BooleanArray(ids.size)
    notificationItems.forEachIndexed { index, notificationItem ->
      ids[index] = notificationItem.id
      mms[index] = notificationItem.isMms
    }

    val intent = Intent(context, DeleteNotificationReceiver::class.java)
      .setAction(DeleteNotificationReceiver.DELETE_NOTIFICATION_ACTION)
      .putExtra(DeleteNotificationReceiver.EXTRA_IDS, ids)
      .putExtra(DeleteNotificationReceiver.EXTRA_MMS, mms)
      .putExtra(DeleteNotificationReceiver.EXTRA_THREAD_IDS, longArrayOf(threadId))
      .makeUniqueToPreventMerging()

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getMarkAsReadIntent(context: Context): PendingIntent {
    val intent = Intent(context, MarkReadReceiver::class.java)
      .setAction(MarkReadReceiver.CLEAR_ACTION)
      .putExtra(MarkReadReceiver.THREAD_IDS_EXTRA, longArrayOf(mostRecentNotification.threadId))
      .putExtra(MarkReadReceiver.NOTIFICATION_ID_EXTRA, notificationId)
      .makeUniqueToPreventMerging()

    return PendingIntent.getBroadcast(context, (threadId * 2).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getQuickReplyIntent(context: Context): PendingIntent {
    val intent: Intent = ConversationIntents.createPopUpBuilder(context, recipient.id, mostRecentNotification.threadId)
      .build()
      .makeUniqueToPreventMerging()

    return PendingIntent.getActivity(context, (threadId * 2).toInt() + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getRemoteReplyIntent(context: Context, replyMethod: ReplyMethod): PendingIntent {
    val intent = Intent(context, RemoteReplyReceiver::class.java)
      .setAction(RemoteReplyReceiver.REPLY_ACTION)
      .putExtra(RemoteReplyReceiver.RECIPIENT_EXTRA, recipient.id)
      .putExtra(RemoteReplyReceiver.REPLY_METHOD, replyMethod)
      .putExtra(RemoteReplyReceiver.EARLIEST_TIMESTAMP, notificationItems.first().timestamp)
      .makeUniqueToPreventMerging()

    return PendingIntent.getBroadcast(context, (threadId * 2).toInt() + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getTurnOffJoinedNotificationsIntent(context: Context): PendingIntent {
    return PendingIntent.getActivity(
      context,
      0,
      TurnOffContactJoinedNotificationsActivity.newIntent(context, threadId),
      PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  override fun toString(): String {
    return "NotificationConversation(threadId=$threadId, notificationItems=$notificationItems, messageCount=$messageCount, hasNewNotifications=${hasNewNotifications()})"
  }
}
