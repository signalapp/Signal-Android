/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Used to track performance metrics for large clusters of similar events.
 * For instance, if you were doing a backup restore and had to important many different kinds of data in an unknown order, you could
 * use this to learn stats around how long each kind of data takes to import.
 *
 * It is assumed that all events are happening serially with no delays in between.
 *
 * The timer tracks things at nanosecond granularity, but presents data as fractional milliseconds for readability.
 */
class EventTimer {

  private val durationsByGroup: MutableMap<String, MutableList<Long>> = mutableMapOf()

  private var startTime = System.nanoTime()
  private var lastTimeNanos: Long = startTime

  fun reset() {
    startTime = System.nanoTime()
    lastTimeNanos = startTime
    durationsByGroup.clear()
  }

  /**
   * Indicates an event in the specified group has finished.
   */
  fun emit(group: String) {
    val now = System.nanoTime()
    val duration = now - lastTimeNanos

    durationsByGroup.getOrPut(group) { mutableListOf() } += duration

    lastTimeNanos = now
  }

  /**
   * Stops the timer and returns a mapping of group -> [EventMetrics], which will tell you various statistics around timings for that group.
   */
  fun stop(): EventTimerResults {
    val data: Map<String, EventMetrics> = durationsByGroup
      .mapValues { entry ->
        val sorted: List<Long> = entry.value.sorted()

        EventMetrics(
          totalTime = sorted.sum().nanoseconds.toDouble(DurationUnit.MILLISECONDS),
          eventCount = sorted.size,
          sortedDurationNanos = sorted
        )
      }

    return EventTimerResults(data)
  }

  class EventTimerResults(data: Map<String, EventMetrics>) : Map<String, EventMetrics> by data {
    val summary by lazy {
      val builder = StringBuilder()

      builder.append("[overall] totalTime: ${data.values.map { it.totalTime }.sum().roundedString(2)} ")

      for (entry in data) {
        builder.append("[${entry.key}] totalTime: ${entry.value.totalTime.roundedString(2)}, count: ${entry.value.eventCount}, p50: ${entry.value.p(50)}, p90: ${entry.value.p(90)}, p99: ${entry.value.p(99)} ")
      }

      builder.toString()
    }
  }

  data class EventMetrics(
    /** The sum of all event durations, in fractional milliseconds. */
    val totalTime: Double,
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
