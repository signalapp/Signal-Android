package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.util.concurrent.TimeUnit

/**
 * Manages deleting call events 8 hours after they've been marked deleted.
 */
class DeletedCallEventManager(
  application: Application
) : TimedEventManager<DeletedCallEventManager.Event>(application, "ExpiringCallEventsManager") {

  companion object {
    private val TAG = Log.tag(DeletedCallEventManager::class.java)

    private val CALL_EVENT_DELETION_LIFESPAN = TimeUnit.HOURS.toMillis(8)
  }

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestTimestamp = SignalDatabase.calls.getOldestDeletionTimestamp()
    if (oldestTimestamp <= 0) return null

    val timeSinceSend = System.currentTimeMillis() - oldestTimestamp
    val delay = (CALL_EVENT_DELETION_LIFESPAN - timeSinceSend).coerceAtLeast(0)
    Log.i(TAG, "The oldest call event needs to be deleted in $delay ms.")

    return Event(delay)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val threshold = System.currentTimeMillis() - CALL_EVENT_DELETION_LIFESPAN
    val deletes = SignalDatabase.calls.deleteCallEventsDeletedBefore(threshold)
    Log.i(TAG, "Deleted $deletes call events before $threshold")
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    setAlarm(application, delay, DeleteCallEventsAlarm::class.java)
  }

  data class Event(val delay: Long)

  class DeleteCallEventsAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(DeleteCallEventsAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      ApplicationDependencies.getDeletedCallEventManager().scheduleIfNecessary()
    }
  }
}
