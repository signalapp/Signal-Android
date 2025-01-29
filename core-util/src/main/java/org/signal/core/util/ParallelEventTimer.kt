/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Used to track performance metrics for large clusters of similar events that are happening simultaneously.
 *
 * Very similar to [EventTimer], but with no assumptions around threading,
 *
 * The timer tracks things at nanosecond granularity, but presents data as fractional milliseconds for readability.
 */
class ParallelEventTimer {

  val durationsByGroup: MutableMap<String, Queue<Long>> = ConcurrentHashMap()
  private var startTime = System.nanoTime()

  fun reset() {
    durationsByGroup.clear()
    startTime = System.nanoTime()
  }

  /**
   * Begin an event associated with a group. You must call [EventStopper.stopEvent] on the returned object in order to indicate the action has completed.
   */
  fun beginEvent(group: String): EventStopper {
    val start = System.nanoTime()
    return EventStopper {
      val duration = System.nanoTime() - start
      durationsByGroup.computeIfAbsent(group) { ConcurrentLinkedQueue() } += duration
    }
  }

  /**
   * Time an event associated with a group.
   */
  inline fun <E> timeEvent(group: String, operation: () -> E): E {
    val start = System.nanoTime()
    val result = operation()
    val duration = System.nanoTime() - start
    durationsByGroup.computeIfAbsent(group) { ConcurrentLinkedQueue() } += duration
    return result
  }

  /**
   * Stops the timer and returns a mapping of group -> [EventMetrics], which will tell you various statistics around timings for that group.
   * It is assumed that all events have been stopped by the time this has been called.
   */
  fun stop(): EventTimerResults {
    val totalDuration = System.nanoTime() - startTime

    val data: Map<String, EventMetrics> = durationsByGroup
      .mapValues { entry ->
        val sorted: List<Long> = entry.value.sorted()

        EventMetrics(
          totalEventTime = sorted.sum().nanoseconds.toDouble(DurationUnit.MILLISECONDS),
          eventCount = sorted.size,
          sortedDurationNanos = sorted
        )
      }

    return EventTimerResults(totalDuration.nanoseconds.toDouble(DurationUnit.MILLISECONDS), data)
  }

  class EventTimerResults(totalWallTime: Double, data: Map<String, EventMetrics>) : Map<String, EventMetrics> by data {
    val summary by lazy {
      val builder = StringBuilder()

      builder.append("[overall] totalWallTime: ${totalWallTime.roundedString(2)}, totalEventTime: ${data.values.map { it.totalEventTime}.sum().roundedString(2)} ")

      for (entry in data) {
        builder.append("[${entry.key}] totalEventTime: ${entry.value.totalEventTime.roundedString(2)}, count: ${entry.value.eventCount}, p50: ${entry.value.p(50)}, p90: ${entry.value.p(90)}, p99: ${entry.value.p(99)} ")
      }

      builder.toString()
    }
  }

  fun interface EventStopper {
    fun stopEvent()
  }

  data class EventMetrics(
    /** The sum of all event times, in fractional milliseconds. If running operations in parallel, this will likely be larger than [totalWallTime]. */
    val totalEventTime: Double,
    /** Total number of events observed.  */
    val eventCount: Int,
    private val sortedDurationNanos: List<Long>
  ) {

    /**
     * Returns the percentile of the duration data (e.g. p50, p90) as a formatted string containing fractional milliseconds rounded to the requested number of decimal places.
     */
    fun p(percentile: Int, decimalPlaces: Int = 2): String {
      return pNanos(percentile).nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(decimalPlaces)
    }

    private fun pNanos(percentile: Int): Long {
      if (sortedDurationNanos.isEmpty()) {
        return 0L
      }

      val index: Float = (percentile / 100f) * (sortedDurationNanos.size - 1)
      val lowerIndex: Int = floor(index).toInt()
      val upperIndex: Int = ceil(index).toInt()

      if (lowerIndex == upperIndex) {
        return sortedDurationNanos[lowerIndex]
      }

      val interpolationFactor: Float = index - lowerIndex
      val lowerValue: Long = sortedDurationNanos[lowerIndex]
      val upperValue: Long = sortedDurationNanos[upperIndex]

      return floor(lowerValue + (upperValue - lowerValue) * interpolationFactor).toLong()
    }
  }
}
