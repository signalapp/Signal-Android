package org.thoughtcrime.securesms.util

import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.model.LocalMetricsEvent
import org.thoughtcrime.securesms.database.model.LocalMetricsSplit
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.util.concurrent.Executor

/**
 * A class for keeping track of local-only metrics.
 *
 * In particular, this class is geared towards timing events that continue over several code boundaries, e.g. sending a message.
 *
 * The process of tracking an event looks something like:
 *  - start("mySpecialId", "send-message")
 *  - split("mySpecialId", "enqueue-job")
 *  - split("mySpecialId", "job-prep")
 *  - split("mySpecialId", "network")
 *  - split("mySpecialId", "ui-refresh")
 *  - end("mySpecialId")
 *
 * These metrics are only ever included in debug logs in an aggregate fashion (i.e. p50, p90, p99) and are never automatically uploaded anywhere.
 */
object LocalMetrics {
  private val TAG: String = Log.tag(LocalMetrics::class.java)

  private val eventsById: MutableMap<String, LocalMetricsEvent> = LRUCache(200)
  private val lastSplitTimeById: MutableMap<String, Long> = LRUCache(200)

  private val executor: Executor = SignalExecutors.newCachedSingleThreadExecutor("signal-LocalMetrics", ThreadUtil.PRIORITY_BACKGROUND_THREAD)
  private val db: LocalMetricsDatabase by lazy { LocalMetricsDatabase.getInstance(ApplicationDependencies.getApplication()) }

  @JvmStatic
  fun getInstance(): LocalMetrics {
    return LocalMetrics
  }

  /**
   * Starts an event with the provided ID and name.
   *
   * @param id A constant that should be unique to this *specific event*. You'll use this same id when calling [split] and [end]. e.g. "message-send-1234"
   * @param name The name of the event. Does not need to be unique. e.g. "message-send"
   */
  fun start(id: String, name: String) {
    val time = System.currentTimeMillis()

    executor.execute {
      eventsById[id] = LocalMetricsEvent(
        createdAt = System.currentTimeMillis(),
        eventId = id,
        eventName = name,
        splits = mutableListOf()
      )
      lastSplitTimeById[id] = time
    }
  }

  /**
   * Marks a split for an event. The duration of the split will be the difference in time between either the event start or the last split, whichever is
   * applicable.
   *
   * If an event with the provided ID does not exist, this is effectively a no-op.
   */
  fun split(id: String, split: String) {
    val time = System.currentTimeMillis()

    executor.execute {
      val lastTime: Long? = lastSplitTimeById[id]
      val splitDoesNotExist: Boolean = eventsById[id]?.splits?.none { it.name == split } ?: true
      if (lastTime != null && splitDoesNotExist) {
        eventsById[id]?.splits?.add(LocalMetricsSplit(split, time - lastTime))
        lastSplitTimeById[id] = time
      }
    }
  }

  /**
   * Stop tracking an event you were previously tracking. All future calls to [split] and [end] will do nothing for this id.
   */
  fun cancel(id: String) {
    executor.execute {
      eventsById.remove(id)
    }
  }

  /**
   * Finishes the event and flushes it to the database.
   */
  fun end(id: String) {
    executor.execute {
      val event: LocalMetricsEvent? = eventsById[id]
      if (event != null) {
        db.insert(System.currentTimeMillis(), event)
        Log.d(TAG, event.toString())
      }
    }
  }

  /**
   * Clears the entire local metrics store.
   */
  fun clear() {
    executor.execute {
      Log.w(TAG, "Clearing local metrics store.")
      db.clear()
    }
  }
}
