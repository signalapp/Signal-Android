package org.signal.core.util.concurrent

import android.os.Handler
import android.os.SystemClock
import org.signal.core.util.logging.Log
import java.util.concurrent.TimeUnit

/**
 * A class that polls active threads at a set interval and logs when multiple threads are BLOCKED.
 */
class DeadlockDetector(private val handler: Handler, private val pollingInterval: Long) {

  private var running = false

  fun start() {
    Log.d(TAG, "Beginning deadlock monitoring.");
    running = true
    handler.postDelayed(this::poll, pollingInterval)
  }

  fun stop() {
    Log.d(TAG, "Ending deadlock monitoring.");
    running = false
    handler.removeCallbacksAndMessages(null)
  }

  private fun poll() {
    val threads = Thread.getAllStackTraces()
    val blocked = threads.filterKeys { thread -> thread.state == Thread.State.BLOCKED }

    if (blocked.size > 1) {
      Log.w(TAG, buildLogString(blocked))
    }

    if (running) {
      handler.postDelayed(this::poll, pollingInterval)
    }
  }

  companion object {
    private val TAG = Log.tag(DeadlockDetector::class.java)

    private fun buildLogString(blocked: Map<Thread, Array<StackTraceElement>>): String {
      val stringBuilder = StringBuilder()
      stringBuilder.append("Found multiple blocked threads! Possible deadlock.\n")

      for (entry in blocked) {
        stringBuilder.append("-- [${entry.key.id}] ${entry.key.name}\n")

        for (element in entry.value) {
          stringBuilder.append("$element\n")
        }

        stringBuilder.append("\n")
      }

      return stringBuilder.toString()
    }
  }
}