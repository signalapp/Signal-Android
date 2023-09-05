package org.thoughtcrime.securesms.database.model

import java.util.concurrent.TimeUnit

data class LocalMetricsEvent(
  val createdAt: Long,
  val eventId: String,
  val eventName: String,
  val splits: MutableList<LocalMetricsSplit>,
  val timeunit: TimeUnit
) {
  override fun toString(): String {
    return "[$eventName] total: ${splits.sumOf { timeunit.convert(it.duration, TimeUnit.NANOSECONDS) }} | ${splits.map { it.toString() }.joinToString(", ")}"
  }
}
