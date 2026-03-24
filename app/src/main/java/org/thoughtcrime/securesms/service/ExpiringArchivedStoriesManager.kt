package org.thoughtcrime.securesms.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.archive.StoryArchiveDuration

/**
 * Manages deleting archived stories after the user-configured retention duration.
 */
class ExpiringArchivedStoriesManager(
  application: Application
) : TimedEventManager<ExpiringArchivedStoriesManager.Event>(application, "ExpiringArchivedStoriesManager") {

  companion object {
    private val TAG = Log.tag(ExpiringArchivedStoriesManager::class.java)
  }

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    if (!SignalStore.story.isArchiveEnabled) return null

    val duration = SignalStore.story.archiveDuration
    if (duration == StoryArchiveDuration.FOREVER) return null

    val oldestTimestamp = SignalDatabase.messages.getOldestArchivedStorySentTimestamp() ?: return null

    val expirationTime = oldestTimestamp + duration.durationMs
    val delay = (expirationTime - System.currentTimeMillis()).coerceAtLeast(0)
    Log.i(TAG, "The oldest archived story needs to be deleted in $delay ms.")

    return Event(delay)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    if (!SignalStore.story.isArchiveEnabled) return

    val duration = SignalStore.story.archiveDuration
    if (duration == StoryArchiveDuration.FOREVER) return

    val threshold = System.currentTimeMillis() - duration.durationMs
    val deletes = SignalDatabase.messages.deleteArchivedStoriesOlderThan(threshold)
    Log.i(TAG, "Deleted $deletes archived stories before $threshold")
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    setAlarm(application, delay, ExpireArchivedStoriesAlarm::class.java)
  }

  data class Event(val delay: Long)

  class ExpireArchivedStoriesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(ExpireArchivedStoriesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      AppDependencies.expireArchivedStoriesManager.scheduleIfNecessary()
    }
  }
}
