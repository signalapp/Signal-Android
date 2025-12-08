/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object MemoryTracker {

  private val TAG = Log.tag(MemoryTracker::class.java)

  private val runtime: Runtime = Runtime.getRuntime()
  private val activityMemoryInfo: ActivityManager.MemoryInfo = ActivityManager.MemoryInfo()
  private val debugMemoryInfo: Debug.MemoryInfo = Debug.MemoryInfo()
  private val handler: Handler = Handler(SignalExecutors.getAndStartHandlerThread("MemoryTracker", ThreadUtil.PRIORITY_BACKGROUND_THREAD).looper)
  private val POLLING_INTERVAL = 5.seconds.inWholeMilliseconds

  private var running = false
  private lateinit var previousAppHeadUsage: AppHeapUsage
  private var increaseMemoryCount = 0

  @JvmStatic
  fun start() {
    Log.d(TAG, "Beginning memory monitoring.")
    running = true
    previousAppHeadUsage = getAppJvmHeapUsage()
    increaseMemoryCount = 0
    handler.postDelayed(this::poll, POLLING_INTERVAL)
  }

  @JvmStatic
  fun stop() {
    Log.d(TAG, "Ending memory monitoring.")
    running = false
    handler.removeCallbacksAndMessages(null)
  }

  fun poll() {
    val currentHeapUsage = getAppJvmHeapUsage()

    if (currentHeapUsage.currentTotalBytes != previousAppHeadUsage.currentTotalBytes) {
      if (currentHeapUsage.currentTotalBytes > previousAppHeadUsage.currentTotalBytes) {
        Log.d(TAG, "The system increased our app JVM heap from ${previousAppHeadUsage.currentTotalBytes.byteDisplay()} to ${currentHeapUsage.currentTotalBytes.byteDisplay()}")
      } else {
        Log.d(TAG, "The system decreased our app JVM heap from ${previousAppHeadUsage.currentTotalBytes.byteDisplay()} to ${currentHeapUsage.currentTotalBytes.byteDisplay()}")
      }
    }

    if (currentHeapUsage.usedBytes >= previousAppHeadUsage.usedBytes) {
      increaseMemoryCount++
    } else {
      Log.d(TAG, "Used memory has decreased from ${previousAppHeadUsage.usedBytes.byteDisplay()} to ${currentHeapUsage.usedBytes.byteDisplay()}")
      increaseMemoryCount = 0
    }

    if (increaseMemoryCount > 0 && increaseMemoryCount % 5 == 0) {
      Log.d(TAG, "Used memory has increased or stayed the same for the last $increaseMemoryCount intervals (${increaseMemoryCount * POLLING_INTERVAL.milliseconds.inWholeSeconds} seconds). Using: ${currentHeapUsage.usedBytes.byteDisplay()}, Free: ${currentHeapUsage.freeBytes.byteDisplay()}, CurrentTotal: ${currentHeapUsage.currentTotalBytes.byteDisplay()}, MaxPossible: ${currentHeapUsage.maxPossibleBytes.byteDisplay()}")
    }

    previousAppHeadUsage = currentHeapUsage

    if (running) {
      handler.postDelayed(this::poll, POLLING_INTERVAL)
    }
  }

  /**
   * Gives us basic memory usage data for our app JVM heap usage. Very fast, ~10 micros on an emulator.
   */
  fun getAppJvmHeapUsage(): AppHeapUsage {
    return AppHeapUsage(
      freeBytes = runtime.freeMemory(),
      currentTotalBytes = runtime.totalMemory(),
      maxPossibleBytes = runtime.maxMemory()
    )
  }

  /**
   * This gives us details stats, but it takes an appreciable amount of time. On an emulator, it can take ~30ms.
   * As a result, we don't want to be calling this regularly for most users.
   */
  fun getDetailedMemoryStats(): DetailedMemoryStats {
    Debug.getMemoryInfo(debugMemoryInfo)

    return DetailedMemoryStats(
      appJavaHeapUsageKb = debugMemoryInfo.getMemoryStat("summary.java-heap")?.toLongOrNull(),
      appNativeHeapUsageKb = debugMemoryInfo.getMemoryStat("summary.native-heap")?.toLongOrNull(),
      codeUsageKb = debugMemoryInfo.getMemoryStat("summary.code")?.toLongOrNull(),
      stackUsageKb = debugMemoryInfo.getMemoryStat("summary.stack")?.toLongOrNull(),
      graphicsUsageKb = debugMemoryInfo.getMemoryStat("summary.graphics")?.toLongOrNull(),
      appOtherUsageKb = debugMemoryInfo.getMemoryStat("summary.private-other")?.toLongOrNull()
    )
  }

  fun getSystemNativeMemoryUsage(context: Context): NativeMemoryUsage {
    val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.getMemoryInfo(activityMemoryInfo)

    return NativeMemoryUsage(
      freeBytes = activityMemoryInfo.availMem,
      totalBytes = activityMemoryInfo.totalMem,
      lowMemory = activityMemoryInfo.lowMemory,
      lowMemoryThreshold = activityMemoryInfo.threshold
    )
  }

  private fun Long.byteDisplay(): String {
    return "$this (${this.bytes.inMebiBytes.roundedString(2)} MiB)"
  }

  data class AppHeapUsage(
    /** The number of bytes that are free to use. */
    val freeBytes: Long,
    /** The current total number of bytes our app could use. This can increase over time as the system increases our allocation. */
    val currentTotalBytes: Long,
    /** The maximum number of bytes that our app could ever be given. */
    val maxPossibleBytes: Long
  ) {
    /** The number of bytes that our app is currently using. */
    val usedBytes: Long
      get() = currentTotalBytes - freeBytes
  }

  data class NativeMemoryUsage(
    val freeBytes: Long,
    val totalBytes: Long,
    val lowMemory: Boolean,
    val lowMemoryThreshold: Long
  ) {
    val usedBytes: Long
      get() = totalBytes - freeBytes
  }

  data class DetailedMemoryStats(
    val appJavaHeapUsageKb: Long?,
    val appNativeHeapUsageKb: Long?,
    val codeUsageKb: Long?,
    val graphicsUsageKb: Long?,
    val stackUsageKb: Long?,
    val appOtherUsageKb: Long?
  )
}
