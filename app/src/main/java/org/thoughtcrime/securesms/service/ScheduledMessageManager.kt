package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.IndividualSendJob
import org.thoughtcrime.securesms.jobs.PushGroupSendJob

/**
 * Manages waking up and sending scheduled messages at the correct time
 */
class ScheduledMessageManager(
  val application: Application
) : TimedEventManager<ScheduledMessageManager.Event>(application, "ScheduledMessagesManager") {

  companion object {
    private val TAG = Log.tag(ScheduledMessageManager::class.java)
  }

  private val messagesTable = SignalDatabase.messages

  init {
    scheduleIfNecessary()
  }

  @Suppress("UsePropertyAccessSyntax")
  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestTimestamp = messagesTable.getOldestScheduledSendTimestamp() ?: return null

    val delay = (oldestTimestamp - System.currentTimeMillis()).coerceAtLeast(0)
    Log.i(TAG, "The next scheduled message needs to be sent in $delay ms.")

    return Event(delay)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val scheduledMessagesToSend = messagesTable.getScheduledMessagesBefore(System.currentTimeMillis())
    for (record in scheduledMessagesToSend) {
      if (SignalDatabase.messages.clearScheduledStatus(record.threadId, record.id)) {
        if (record.recipient.isPushGroup) {
          PushGroupSendJob.enqueue(application, ApplicationDependencies.getJobManager(), record.id, record.recipient.id, emptySet())
        } else {
          IndividualSendJob.enqueue(application, ApplicationDependencies.getJobManager(), record.id, record.recipient)
        }
      } else {
        Log.i(TAG, "messageId=${record.id} was not a scheduled message, ignoring")
      }
    }
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, delay: Long) {
    trySetExactAlarm(application, System.currentTimeMillis() + delay, ScheduledMessagesAlarm::class.java)
  }

  data class Event(val delay: Long)

  class ScheduledMessagesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(ScheduledMessagesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      ApplicationDependencies.getScheduledMessageManager().scheduleIfNecessary()
    }
  }
}
