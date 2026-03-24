/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.concurrent

import android.os.Debug
import android.os.Looper
import androidx.annotation.MainThread
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Attempts to detect ANR's by posting runnables to the main thread and detecting if they've been run within the [anrThreshold].
 * If an ANR is detected, it is logged, and the [anrSaver] is called with the series of thread dumps that were taken of the main thread.
 *
 * The detection of an ANR will cause an internal user to crash.
 */
object AnrDetector {

  private val TAG = Log.tag(AnrDetector::class.java)

  private var thread: AnrDetectorThread? = null

  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz", Locale.US)

  @JvmStatic
  @MainThread
  fun start(anrThreshold: Long = 5.seconds.inWholeMilliseconds, isInternal: () -> Boolean, anrSaver: (String) -> Unit) {
    thread?.end()
    thread = null

    thread = AnrDetectorThread(anrThreshold.milliseconds, isInternal, anrSaver)
    thread!!.start()
  }

  @JvmStatic
  @MainThread
  fun stop() {
    thread?.end()
    thread = null
  }

  private class AnrDetectorThread(
    private val anrThreshold: Duration,
    private val isInternal: () -> Boolean,
    private val anrSaver: (String) -> Unit
  ) : Thread("signal-anr") {

    @Volatile
    private var uiRan = false
    private val uiRunnable = Runnable {
      uiRan = true
    }

    @Volatile
    private var stopped = false

    override fun run() {
      while (!stopped) {
        uiRan = false
        ThreadUtil.postToMain(uiRunnable)

        val intervalCount = 5
        val intervalDuration = anrThreshold.inWholeMilliseconds / intervalCount
        if (intervalDuration == 0L) {
          throw IllegalStateException("ANR threshold is too small!")
        }

        val dumps = mutableListOf<String>()

        for (i in 1..intervalCount) {
          if (stopped) {
            Log.i(TAG, "Thread shutting down during intervals.")
            return
          }

          ThreadUtil.sleep(intervalDuration)

          if (!uiRan) {
            dumps += getMainThreadDump()
          } else {
            dumps.clear()
          }
        }

        if (!uiRan && !Debug.isDebuggerConnected() && !Debug.waitingForDebugger()) {
          Log.w(TAG, "Failed to post to main in ${anrThreshold.inWholeMilliseconds} ms! Likely ANR!")

          val dumpString = dumps.joinToString(separator = "\n\n")
          Log.w(TAG, "Main thread dumps:\n$dumpString")

          ThreadUtil.cancelRunnableOnMain(uiRunnable)
          anrSaver(dumpString)

          if (isInternal()) {
            Log.e(TAG, "Internal user -- crashing!")
            throw SignalAnrException()
          }
        }

        dumps.clear()
      }

      Log.i(TAG, "Thread shutting down.")
    }

    fun end() {
      stopped = true
    }

    private fun getMainThreadDump(): String {
      val dump: Map<Thread, Array<StackTraceElement>> = Thread.getAllStackTraces()
      val mainThread = Looper.getMainLooper().thread
      val date = dateFormat.format(Date())
      val dumpString = dump[mainThread]?.joinToString(separator = "\n") ?: "Not available."

      return "--- $date:\n$dumpString"
    }
  }

  private class SignalAnrException : RuntimeException()
}
