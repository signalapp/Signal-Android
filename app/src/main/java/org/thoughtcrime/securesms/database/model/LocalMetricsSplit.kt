package org.thoughtcrime.securesms.database.model

import org.signal.core.util.roundedString
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

data class LocalMetricsSplit(
  val name: String,
  val duration: Long,
  val timeunit: TimeUnit = TimeUnit.MILLISECONDS
) {
  override fun toString(): String {
    return "$name: ${duration.fractionalMillis(timeunit)}"
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
