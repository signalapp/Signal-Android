package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.UnreadReminderJob
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Manages enqueueing [UnreadReminderJob] when a thread has an unread message/call that exceeds the reminder threshold.
 */
class UnreadReminderManager(
  val application: Application
) : TimedEventManager<UnreadReminderManager.Event>(application, "UnreadReminderManager") {

  companion object {
    private val TAG = Log.tag(UnreadReminderManager::class.java)
    private val reminderThreshold: Long
      get() = RemoteConfig.unreadReminderIntervalSeconds.seconds.inWholeMilliseconds

    val MAX_UNREAD_MESSAGE_AGE = 14.days
  }

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val messageThreadIds = SignalDatabase.threads.getMutedThreadIds(reminderThreshold)
    val (messageThreadId, messageTimestamp) = SignalDatabase.messages.getOldestUnreadMessage(messageThreadIds)

    return if (messageThreadId == -1L) {
      Log.i(TAG, "No existing unread message or calls from a qualifying thread.")
      cancelAlarm(application, UnreadReminderAlarm::class.java)
      null
    } else {
      val delay = (messageTimestamp + reminderThreshold - System.currentTimeMillis()).coerceAtLeast(0)
      Log.i(TAG, "The next unread reminder needs to fire in $delay ms for a message in thread $messageThreadId.")
      Event(delay, messageThreadId)
    }
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    Log.i(TAG, "Executing event $event")
    val lastReminderTime = SignalDatabase.threads.getUnreadReminderTime(event.threadId)
    SignalDatabase.threads.setUnreadReminderTime(event.threadId, System.currentTimeMillis())
    UnreadReminderJob.enqueue(event.threadId, lastReminderTime)
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    setAlarm(application, delay, UnreadReminderAlarm::class.java)
  }

  data class Event(val delay: Long, val threadId: Long)

  class UnreadReminderAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(UnreadReminderAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      AppDependencies.unreadReminderManager.scheduleIfNecessary()
    }
  }
}
