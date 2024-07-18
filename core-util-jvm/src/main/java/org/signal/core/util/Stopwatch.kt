/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

/**
 * Simple utility to easily track the time a multi-step operation takes via splits.
 *
 * e.g.
 *
 * ```kotlin
 * val stopwatch = Stopwatch("my-event")
 * stopwatch.split("split-1")
 * stopwatch.split("split-2")
 * stopwatch.split("split-3")
 * stopwatch.stop(TAG)
 * ```
 */
class Stopwatch @JvmOverloads constructor(private val title: String, private val decimalPlaces: Int = 0) {
  private val startTimeNanos: Long = System.nanoTime()
  private val splits: MutableList<Split> = mutableListOf()

  /**
   * Create a new split between now and the last event.
   */
  fun split(label: String) {
    val now = System.nanoTime()

    val previousTime = if (splits.isEmpty()) {
      startTimeNanos
    } else {
      splits.last().nanoTime
    }

    splits += Split(
      nanoTime = now,
      durationNanos = now - previousTime,
      label = label
    )
  }

  /**
   * Stops the stopwatch and logs the results with the provided tag.
   */
  fun stop(tag: String) {
    Log.d(tag, stopAndGetLogString())
  }

  /**
   * Similar to [stop], but instead of logging directly, this will return the log string.
   */
  fun stopAndGetLogString(): String {
    val now = System.nanoTime()

    splits += Split(
      nanoTime = now,
      durationNanos = now - startTimeNanos,
      label = "total"
    )

    val splitString = splits
      .joinToString(separator = ", ", transform = { it.displayString(decimalPlaces) })

    return "[$title] $splitString"
  }

  private data class Split(val nanoTime: Long, val durationNanos: Long, val label: String) {
    fun displayString(decimalPlaces: Int): String {
      val timeMs: String = durationNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(decimalPlaces)
      return "$label: $timeMs"
    }
  }
}

/**
 * Logs how long it takes to perform the operation.
 */
inline fun <T> logTime(tag: String, label: String, decimalPlaces: Int = 0, block: () -> T): T {
  val result = measureTimedValue(block)
  Log.d(tag, "$label: ${result.duration.toDouble(DurationUnit.MILLISECONDS).roundedString(decimalPlaces)}")
  return result.value
}
