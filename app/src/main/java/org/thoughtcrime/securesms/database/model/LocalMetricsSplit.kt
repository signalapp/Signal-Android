package org.thoughtcrime.securesms.database.model

import java.util.concurrent.TimeUnit

data class LocalMetricsSplit(
  val name: String,
  val duration: Long,
  val timeunit: TimeUnit = TimeUnit.MILLISECONDS
) {
  override fun toString(): String {
    return "$name: ${timeunit.convert(duration, TimeUnit.NANOSECONDS)}"
  }
}
