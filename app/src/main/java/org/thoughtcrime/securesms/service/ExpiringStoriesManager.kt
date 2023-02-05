package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.concurrent.TimeUnit

/**
 * Manages deleting stories 24 hours after they've been sent.
 */
class ExpiringStoriesManager(
  application: Application
) : TimedEventManager<ExpiringStoriesManager.Event>(application, "ExpiringStoriesManager") {

  companion object {
    private val TAG = Log.tag(ExpiringStoriesManager::class.java)

    private val STORY_LIFESPAN = TimeUnit.HOURS.toMillis(24)
  }

  private val mmsDatabase = SignalDatabase.messages

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestTimestamp = mmsDatabase.getOldestStorySendTimestamp(SignalStore.storyValues().userHasViewedOnboardingStory) ?: return null

    val timeSinceSend = System.currentTimeMillis() - oldestTimestamp
    val delay = (STORY_LIFESPAN - timeSinceSend).coerceAtLeast(0)
    Log.i(TAG, "The oldest story needs to be deleted in $delay ms.")

    return Event(delay)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val threshold = System.currentTimeMillis() - STORY_LIFESPAN
    val deletes = mmsDatabase.deleteStoriesOlderThan(threshold, SignalStore.storyValues().userHasViewedOnboardingStory)
    Log.i(TAG, "Deleted $deletes stories before $threshold")
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, delay: Long) {
    setAlarm(application, delay, ExpireStoriesAlarm::class.java)
  }

  data class Event(val delay: Long)

  class ExpireStoriesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(ExpireStoriesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      ApplicationDependencies.getExpireStoriesManager().scheduleIfNecessary()
    }
  }
}
