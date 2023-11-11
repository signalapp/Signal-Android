package org.thoughtcrime.securesms.database.model

import org.signal.core.util.roundedString
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

data class LocalMetricsEvent(
  val createdAt: Long,
  val eventId: String,
  val eventName: String,
  val splits: MutableList<LocalMetricsSplit>,
  val timeunit: TimeUnit
) {
  override fun toString(): String {
    return "[$eventName] total: ${splits.sumOf { it.duration }.fractionalMillis(timeunit)} | ${splits.map { it.toString() }.joinToString(", ")}"
  }

  private fun Long.fractionalMillis(timeunit: TimeUnit): String {
    val places = when (timeunit) {
      TimeUnit.MICROSECONDS -> 3
      TimeUnit.NANOSECONDS -> 6
      else -> 0
    }
    return this.nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(places)
  }
}
