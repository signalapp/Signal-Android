package org.signal.core.util.concurrent

import java.util.concurrent.Executor

/**
 * Like [org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor], but manages independent queues keyed by a string.
 *
 * Each key gets its own active/next pair, so tasks with different keys can run concurrently on the
 * backing executor. Within a given key, only two tasks exist at a time: the currently running one
 * and the most recently enqueued one. Any previously-pending task for that key is replaced.
 *
 * Idle keys are cleaned up automatically when their work completes.
 */
class KeyedSerialMonoLifoExecutor(private val executor: Executor) {

  private val entries = mutableMapOf<String, TaskEntry>()

  @Synchronized
  fun execute(key: String, command: Runnable) {
    enqueue(key, command)
  }

  /**
   * @return True if a pending task for this key was replaced, otherwise false.
   */
  @Synchronized
  fun enqueue(key: String, command: Runnable): Boolean {
    val entry = entries.getOrPut(key) { TaskEntry() }
    val performedReplace = entry.next != null

    entry.next = Runnable {
      try {
        command.run()
      } finally {
        scheduleNext(key)
      }
    }

    if (entry.active == null) {
      scheduleNext(key)
    }

    return performedReplace
  }

  @Synchronized
  private fun scheduleNext(key: String) {
    val entry = entries[key] ?: return

    entry.active = entry.next
    entry.next = null

    if (entry.active != null) {
      executor.execute(entry.active)
    } else {
      entries.remove(key)
    }
  }

  private class TaskEntry {
    var active: Runnable? = null
    var next: Runnable? = null
  }
}
