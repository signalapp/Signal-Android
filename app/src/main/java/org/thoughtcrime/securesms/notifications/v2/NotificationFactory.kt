package org.thoughtcrime.securesms.notifications.v2

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.TransactionTooLargeException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.notifications.DefaultMessageNotifier
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.BubbleUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

private val TAG = Log.tag(NotificationFactory::class.java)

/**
 * Given a notification state consisting of conversations of messages, show appropriate system notifications.
 */
object NotificationFactory {

  fun notify(
    context: Context,
    state: NotificationStateV2,
    visibleThreadId: Long,
    targetThreadId: Long,
    defaultBubbleState: BubbleUtil.BubbleState,
    lastAudibleNotification: Long,
    notificationConfigurationChanged: Boolean,
    alertOverrides: Set<Long>
  ): Set<Long> {
    if (state.isEmpty) {
      Log.d(TAG, "State is empty, bailing")
      return emptySet()
    }
    val threadsThatNewlyAlerted: MutableSet<Long> = mutableSetOf()

    if (Build.VERSION.SDK_INT >= 23 || state.conversations.size == 1) {
      state.conversations.forEach { conversation ->
        if (conversation.threadId == visibleThreadId && conversation.hasNewNotifications()) {
          Log.internal().i(TAG, "Thread is visible, notifying in thread. notificationId: ${conversation.notificationId}")
          notifyInThread(context, conversation.recipient, lastAudibleNotification)
        } else if (notificationConfigurationChanged || conversation.hasNewNotifications() || alertOverrides.contains(conversation.threadId)) {

          if (conversation.hasNewNotifications()) {
            threadsThatNewlyAlerted += conversation.threadId
          }

          notifyForConversation(
            context = context,
            conversation = conversation,
            targetThreadId = targetThreadId,
            defaultBubbleState = defaultBubbleState,
            shouldAlert = (conversation.hasNewNotifications() || alertOverrides.contains(conversation.threadId)) && !conversation.mostRecentNotification.individualRecipient.isSelf
          )
        }
      }
    }

    if ((state.conversations.size > 1 && threadsThatNewlyAlerted.isNotEmpty()) || ServiceUtil.getNotificationManager(context).isDisplayingSummaryNotification()) {
      val builder: NotificationBuilder = NotificationBuilder.create(context)

      builder.apply {
        setSmallIcon(R.drawable.ic_notification)
        setColor(ContextCompat.getColor(context, R.color.core_ultramarine))
        setCategory(NotificationCompat.CATEGORY_MESSAGE)
        setGroup(DefaultMessageNotifier.NOTIFICATION_GROUP)
        setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        setChannelId(NotificationChannels.getMessagesChannel(context))
        setContentTitle(context.getString(R.string.app_name))
        setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), 0))
        setGroupSummary(true)
        setSubText(context.getString(R.string.MessageNotifier_d_new_messages_in_d_conversations, state.messageCount, state.threadCount))
        setContentInfo(state.messageCount.toString())
        setNumber(state.messageCount)
        setSummaryContentText(state.mostRecentSender)
        setDeleteIntent(state.getDeleteIntent(context))
        setWhen(state.mostRecentNotification)
        addMarkAsReadAction(state)
        addMessages(state)
        setOnlyAlertOnce(!state.notificationItems.any { it.isNewNotification })
        setPriority(TextSecurePreferences.getNotificationPriority(context))
        setLights()
        setAlarms(state.mostRecentSender)
        setTicker(state.mostRecentNotification.getStyledPrimaryText(context, true))
      }

      Log.d(TAG, "showing summary notification")
      NotificationManagerCompat.from(context).safelyNotify(context, null, NotificationIds.MESSAGE_SUMMARY, builder.build())
    }

    return threadsThatNewlyAlerted
  }

  private fun notifyForConversation(
    context: Context,
    conversation: NotificationConversation,
    targetThreadId: Long,
    defaultBubbleState: BubbleUtil.BubbleState,
    shouldAlert: Boolean
  ) {
    val builder: NotificationBuilder = NotificationBuilder.create(context)

    builder.apply {
      setSmallIcon(R.drawable.ic_notification)
      setColor(ContextCompat.getColor(context, R.color.core_ultramarine))
      setCategory(NotificationCompat.CATEGORY_MESSAGE)
      setGroup(DefaultMessageNotifier.NOTIFICATION_GROUP)
      setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
      setChannelId(conversation.getChannelId(context))
      setContentTitle(conversation.getContentTitle(context))
      setLargeIcon(conversation.getLargeIcon(context))
      addPerson(conversation.recipient)
      setShortcutId(ConversationUtil.getShortcutId(conversation.recipient))
      setContentInfo(conversation.messageCount.toString())
      setNumber(conversation.messageCount)
      setContentText(conversation.getContentText(context))
      setContentIntent(conversation.getPendingIntent(context))
      setDeleteIntent(conversation.getDeleteIntent(context))
      setSortKey(conversation.sortKey.toString())
      setWhen(conversation)
      addReplyActions(conversation)
      setOnlyAlertOnce(!shouldAlert)
      addMessages(conversation)
      setPriority(TextSecurePreferences.getNotificationPriority(context))
      setLights()
      setAlarms(conversation.recipient)
      setTicker(conversation.mostRecentNotification.getStyledPrimaryText(context, true))
      setBubbleMetadata(conversation, if (targetThreadId == conversation.threadId) defaultBubbleState else BubbleUtil.BubbleState.HIDDEN)
    }

    if (conversation.isOnlyContactJoinedEvent) {
      builder.addTurnOffJoinedNotificationsAction(conversation.getTurnOffJoinedNotificationsIntent(context))
    }

    NotificationManagerCompat.from(context).safelyNotify(context, conversation.recipient, conversation.notificationId, builder.build())
  }

  private fun notifyInThread(context: Context, recipient: Recipient, lastAudibleNotification: Long) {
    if (!TextSecurePreferences.isInThreadNotifications(context) ||
      ServiceUtil.getAudioManager(context).ringerMode != AudioManager.RINGER_MODE_NORMAL ||
      (System.currentTimeMillis() - lastAudibleNotification) < DefaultMessageNotifier.MIN_AUDIBLE_PERIOD_MILLIS
    ) {
      return
    }

    val uri: Uri = if (NotificationChannels.supported()) {
      NotificationChannels.getMessageRingtone(context, recipient) ?: NotificationChannels.getMessageRingtone(context)
    } else {
      recipient.messageRingtone ?: TextSecurePreferences.getNotificationRingtone(context)
    }

    if (uri.toString().isEmpty()) {
      Log.d(TAG, "ringtone uri is empty")
      return
    }

    val ringtone = RingtoneManager.getRingtone(context, uri)

    if (ringtone == null) {
      Log.w(TAG, "ringtone is null")
      return
    }

    if (Build.VERSION.SDK_INT >= 21) {
      ringtone.audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .build()
    } else {
      @Suppress("DEPRECATION")
      ringtone.streamType = AudioManager.STREAM_NOTIFICATION
    }

    ringtone.play()
  }

  fun notifyMessageDeliveryFailed(context: Context, recipient: Recipient, threadId: Long, visibleThread: Long) {
    if (threadId == visibleThread) {
      notifyInThread(context, recipient, 0)
      return
    }

    val intent: Intent = ConversationIntents.createBuilder(context, recipient.id, threadId)
      .build()
      .makeUniqueToPreventMerging()

    val builder: NotificationBuilder = NotificationBuilder.create(context)

    builder.apply {
      setSmallIcon(R.drawable.ic_notification)
      setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_action_warning_red))
      setContentTitle(context.getString(R.string.MessageNotifier_message_delivery_failed))
      setContentText(context.getString(R.string.MessageNotifier_failed_to_deliver_message))
      setTicker(context.getString(R.string.MessageNotifier_error_delivering_message))
      setContentIntent(PendingIntent.getActivity(context, 0, intent, 0))
      setAutoCancel(true)
      setAlarms(recipient)
      setChannelId(NotificationChannels.FAILURES)
    }

    NotificationManagerCompat.from(context).safelyNotify(context, recipient, threadId.toInt(), builder.build())
  }

  private fun NotificationManager.isDisplayingSummaryNotification(): Boolean {
    if (Build.VERSION.SDK_INT >= 23) {
      try {
        return activeNotifications.any { notification -> notification.id == NotificationIds.MESSAGE_SUMMARY }
      } catch (e: Throwable) {
      }
    }
    return false
  }

  private fun NotificationManagerCompat.safelyNotify(context: Context, threadRecipient: Recipient?, notificationId: Int, notification: Notification) {
    try {
      notify(notificationId, notification)
      Log.internal().i(TAG, "Posted notification: $notification")
    } catch (e: SecurityException) {
      Log.i(TAG, "Security exception when posting notification, clearing ringtone")
      if (threadRecipient != null) {
        SignalExecutors.BOUNDED.execute {
          DatabaseFactory.getRecipientDatabase(context).setMessageRingtone(threadRecipient.id, null)
          NotificationChannels.updateMessageRingtone(context, threadRecipient, null)
        }
      }
    } catch (runtimeException: RuntimeException) {
      if (runtimeException.cause is TransactionTooLargeException) {
        Log.e(TAG, "Transaction too large", runtimeException)
      } else {
        throw runtimeException
      }
    }
  }
}
