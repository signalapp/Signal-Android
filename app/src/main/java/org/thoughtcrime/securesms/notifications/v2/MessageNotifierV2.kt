package org.thoughtcrime.securesms.notifications.v2

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import me.leolin.shortcutbadger.ShortcutBadger
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.notifications.MessageNotifier
import org.thoughtcrime.securesms.notifications.MessageNotifier.ReminderReceiver
import org.thoughtcrime.securesms.notifications.NotificationCancellationHelper
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.BubbleUtil.BubbleState
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder
import org.whispersystems.signalservice.internal.util.Util
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.MutableMap.MutableEntry
import kotlin.math.max

/**
 * MessageNotifier implementation using the new system for creating and showing notifications.
 */
class MessageNotifierV2(context: Application) : MessageNotifier {
  @Volatile private var visibleThread: Long = -1
  @Volatile private var lastDesktopActivityTimestamp: Long = -1
  @Volatile private var lastAudibleNotification: Long = -1
  @Volatile private var lastScheduledReminder: Long = 0
  @Volatile private var previousLockedStatus: Boolean = KeyCachingService.isLocked(context)
  @Volatile private var previousPrivacyPreference: NotificationPrivacyPreference = SignalStore.settings().messageNotificationsPrivacy
  @Volatile private var previousState: NotificationStateV2 = NotificationStateV2.EMPTY

  private val threadReminders: MutableMap<Long, Reminder> = ConcurrentHashMap()
  private val stickyThreads: MutableMap<Long, StickyThread> = mutableMapOf()

  private val executor = CancelableExecutor()

  override fun setVisibleThread(threadId: Long) {
    visibleThread = threadId
    stickyThreads.remove(threadId)
  }

  override fun getVisibleThread(): Long {
    return visibleThread
  }

  override fun clearVisibleThread() {
    setVisibleThread(-1)
  }

  override fun setLastDesktopActivityTimestamp(timestamp: Long) {
    lastDesktopActivityTimestamp = timestamp
  }

  override fun notifyMessageDeliveryFailed(context: Context, recipient: Recipient, threadId: Long) {
    NotificationFactory.notifyMessageDeliveryFailed(context, recipient, threadId, visibleThread)
  }

  override fun notifyProofRequired(context: Context, recipient: Recipient, threadId: Long) {
    NotificationFactory.notifyProofRequired(context, recipient, threadId, visibleThread)
  }

  override fun cancelDelayedNotifications() {
    executor.cancel()
  }

  override fun updateNotification(context: Context) {
    updateNotification(context, -1, false, 0, BubbleState.HIDDEN)
  }

  override fun updateNotification(context: Context, threadId: Long) {
    if (System.currentTimeMillis() - lastDesktopActivityTimestamp < DESKTOP_ACTIVITY_PERIOD) {
      Log.i(TAG, "Scheduling delayed notification...")
      executor.enqueue(context, threadId)
    } else {
      updateNotification(context, threadId, true)
    }
  }

  override fun updateNotification(context: Context, threadId: Long, defaultBubbleState: BubbleState) {
    updateNotification(context, threadId, false, 0, defaultBubbleState)
  }

  override fun updateNotification(context: Context, threadId: Long, signal: Boolean) {
    updateNotification(context, threadId, signal, 0, BubbleState.HIDDEN)
  }

  /**
   * @param signal is no longer used
   * @param reminderCount is not longer used
   */
  override fun updateNotification(
    context: Context,
    threadId: Long,
    signal: Boolean,
    reminderCount: Int,
    defaultBubbleState: BubbleState
  ) {
    if (!SignalStore.settings().isMessageNotificationsEnabled) {
      return
    }

    val currentLockStatus: Boolean = KeyCachingService.isLocked(context)
    val currentPrivacyPreference: NotificationPrivacyPreference = SignalStore.settings().messageNotificationsPrivacy
    val notificationConfigurationChanged: Boolean = currentLockStatus != previousLockedStatus || currentPrivacyPreference != previousPrivacyPreference
    previousLockedStatus = currentLockStatus
    previousPrivacyPreference = currentPrivacyPreference

    if (notificationConfigurationChanged) {
      stickyThreads.clear()
    }

    Log.internal().i(TAG, "sticky thread: $stickyThreads")
    var state: NotificationStateV2 = NotificationStateProvider.constructNotificationState(context, stickyThreads)
    Log.internal().i(TAG, "state: $state")

    val displayedNotifications: Set<Int>? = ServiceUtil.getNotificationManager(context).getDisplayedNotificationIds().getOrNull()
    if (displayedNotifications != null) {
      val cleanedUpThreadIds: MutableSet<Long> = mutableSetOf()
      state.conversations.filterNot { it.hasNewNotifications() || displayedNotifications.contains(it.notificationId) }
        .forEach { conversation ->
          cleanedUpThreadIds += conversation.threadId
          conversation.notificationItems.forEach { item ->
            val messageDatabase: MessageDatabase = if (item.isMms) SignalDatabase.mms else SignalDatabase.sms
            messageDatabase.markAsNotified(item.id)
          }
        }
      if (cleanedUpThreadIds.isNotEmpty()) {
        Log.i(TAG, "Cleaned up ${cleanedUpThreadIds.size} thread(s) with dangling notifications")
        state = NotificationStateV2(state.conversations.filterNot { cleanedUpThreadIds.contains(it.threadId) })
      }
    }

    val retainStickyThreadIds: Set<Long> = state.getThreadsWithMostRecentNotificationFromSelf()
    stickyThreads.keys.retainAll { retainStickyThreadIds.contains(it) }

    if (state.isEmpty) {
      Log.i(TAG, "State is empty, cancelling all notifications")
      NotificationCancellationHelper.cancelAllMessageNotifications(context, stickyThreads.map { it.value.notificationId }.toSet())
      updateBadge(context, 0)
      clearReminderInternal(context)
      return
    }

    val alertOverrides: Set<Long> = threadReminders.filter { (_, reminder) -> reminder.lastNotified < System.currentTimeMillis() - REMINDER_TIMEOUT }.keys

    val threadsThatAlerted: Set<Long> = NotificationFactory.notify(
      context = ContextThemeWrapper(context, R.style.TextSecure_LightTheme),
      state = state,
      visibleThreadId = visibleThread,
      targetThreadId = threadId,
      defaultBubbleState = defaultBubbleState,
      lastAudibleNotification = lastAudibleNotification,
      notificationConfigurationChanged = notificationConfigurationChanged,
      alertOverrides = alertOverrides,
      previousState = previousState
    )

    previousState = state
    lastAudibleNotification = System.currentTimeMillis()

    updateReminderTimestamps(context, alertOverrides, threadsThatAlerted)

    ServiceUtil.getNotificationManager(context).cancelOrphanedNotifications(context, state, stickyThreads.map { it.value.notificationId }.toSet())
    updateBadge(context, state.messageCount)

    val smsIds: MutableList<Long> = mutableListOf()
    val mmsIds: MutableList<Long> = mutableListOf()
    for (item: NotificationItemV2 in state.notificationItems) {
      if (item.isMms) {
        mmsIds.add(item.id)
      } else {
        smsIds.add(item.id)
      }
    }
    SignalDatabase.mmsSms.setNotifiedTimestamp(System.currentTimeMillis(), smsIds, mmsIds)

    Log.i(TAG, "threads: ${state.threadCount} messages: ${state.messageCount}")
  }

  override fun clearReminder(context: Context) {
    // Intentionally left blank
  }

  override fun addStickyThread(threadId: Long, earliestTimestamp: Long) {
    stickyThreads[threadId] = StickyThread(threadId, NotificationIds.getNotificationIdForThread(threadId), earliestTimestamp)
  }

  override fun removeStickyThread(threadId: Long) {
    stickyThreads.remove(threadId)
  }

  private fun updateReminderTimestamps(context: Context, alertOverrides: Set<Long>, threadsThatAlerted: Set<Long>) {
    if (SignalStore.settings().messageNotificationsRepeatAlerts == 0) {
      return
    }

    val iterator: MutableIterator<MutableEntry<Long, Reminder>> = threadReminders.iterator()
    while (iterator.hasNext()) {
      val entry: MutableEntry<Long, Reminder> = iterator.next()
      val (id: Long, reminder: Reminder) = entry
      if (alertOverrides.contains(id)) {
        val notifyCount: Int = reminder.count + 1
        if (notifyCount >= SignalStore.settings().messageNotificationsRepeatAlerts) {
          iterator.remove()
        } else {
          entry.setValue(Reminder(lastAudibleNotification, notifyCount))
        }
      }
    }

    for (alertedThreadId: Long in threadsThatAlerted) {
      threadReminders[alertedThreadId] = Reminder(lastAudibleNotification)
    }

    if (threadReminders.isNotEmpty()) {
      scheduleReminder(context)
    } else {
      lastScheduledReminder = 0
    }
  }

  private fun scheduleReminder(context: Context) {
    val timeout: Long = if (lastScheduledReminder != 0L) {
      max(TimeUnit.SECONDS.toMillis(5), REMINDER_TIMEOUT - (System.currentTimeMillis() - lastScheduledReminder))
    } else {
      REMINDER_TIMEOUT
    }

    val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)
    val pendingIntent: PendingIntent = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
    alarmManager?.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent)
    lastScheduledReminder = System.currentTimeMillis()
  }

  private fun clearReminderInternal(context: Context) {
    lastScheduledReminder = 0
    threadReminders.clear()

    val pendingIntent: PendingIntent? = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_CANCEL_CURRENT)
    if (pendingIntent != null) {
      val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)
      alarmManager?.cancel(pendingIntent)
    }
  }

  companion object {
    val TAG: String = Log.tag(MessageNotifierV2::class.java)

    private val REMINDER_TIMEOUT: Long = TimeUnit.MINUTES.toMillis(2)
    val MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(2)
    val DESKTOP_ACTIVITY_PERIOD = TimeUnit.MINUTES.toMillis(1)

    const val EXTRA_REMOTE_REPLY = "extra_remote_reply"
    const val NOTIFICATION_GROUP = "messages"

    private fun updateBadge(context: Context, count: Int) {
      try {
        if (count == 0) ShortcutBadger.removeCount(context) else ShortcutBadger.applyCount(context, count)
      } catch (t: Throwable) {
        Log.w(TAG, t)
      }
    }
  }

  data class StickyThread(val threadId: Long, val notificationId: Int, val earliestTimestamp: Long)
  private data class Reminder(val lastNotified: Long, val count: Int = 0)
}

private fun StatusBarNotification.isMessageNotification(): Boolean {
  return id != NotificationIds.MESSAGE_SUMMARY &&
    id != KeyCachingService.SERVICE_RUNNING_ID &&
    id != IncomingMessageObserver.FOREGROUND_ID &&
    id != NotificationIds.PENDING_MESSAGES &&
    !CallNotificationBuilder.isWebRtcNotification(id)
}

private fun NotificationManager.getDisplayedNotificationIds(): Result<Set<Int>> {
  if (Build.VERSION.SDK_INT < 24) {
    return Result.failure(UnsupportedOperationException("SDK level too low"))
  }

  return try {
    Result.success(activeNotifications.filter { it.isMessageNotification() }.map { it.id }.toSet())
  } catch (e: Throwable) {
    Log.w(MessageNotifierV2.TAG, e)
    Result.failure(e)
  }
}

private fun NotificationManager.cancelOrphanedNotifications(context: Context, state: NotificationStateV2, stickyNotifications: Set<Int>) {
  if (Build.VERSION.SDK_INT < 24) {
    return
  }

  try {
    activeNotifications.filter { it.isMessageNotification() && !stickyNotifications.contains(it.id) }
      .map { it.id }
      .filterNot { state.notificationIds.contains(it) }
      .forEach { id ->
        Log.d(MessageNotifierV2.TAG, "Cancelling orphaned notification: $id")
        NotificationCancellationHelper.cancel(context, id)
      }

    NotificationCancellationHelper.cancelMessageSummaryIfSoleNotification(context)
  } catch (e: Throwable) {
    Log.w(MessageNotifierV2.TAG, e)
  }
}

private class CancelableExecutor {
  private val executor: Executor = Executors.newSingleThreadExecutor()
  private val tasks: MutableSet<DelayedNotification> = mutableSetOf()

  fun enqueue(context: Context, threadId: Long) {
    execute(DelayedNotification(context, threadId))
  }

  private fun execute(runnable: DelayedNotification) {
    synchronized(tasks) { tasks.add(runnable) }
    val wrapper = Runnable {
      runnable.run()
      synchronized(tasks) { tasks.remove(runnable) }
    }
    executor.execute(wrapper)
  }

  fun cancel() {
    synchronized(tasks) {
      for (task in tasks) {
        task.cancel()
      }
    }
  }

  private class DelayedNotification constructor(private val context: Context, private val threadId: Long) : Runnable {
    private val canceled = AtomicBoolean(false)
    private val delayUntil: Long = System.currentTimeMillis() + DELAY

    override fun run() {
      val delayMillis = delayUntil - System.currentTimeMillis()
      Log.i(TAG, "Waiting to notify: $delayMillis")
      if (delayMillis > 0) {
        Util.sleep(delayMillis)
      }
      if (!canceled.get()) {
        Log.i(TAG, "Not canceled, notifying...")
        ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId, true)
        ApplicationDependencies.getMessageNotifier().cancelDelayedNotifications()
      } else {
        Log.w(TAG, "Canceled, not notifying...")
      }
    }

    fun cancel() {
      canceled.set(true)
    }

    companion object {
      private val DELAY = TimeUnit.SECONDS.toMillis(5)
      private val TAG = Log.tag(DelayedNotification::class.java)
    }
  }
}
