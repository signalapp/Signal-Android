package org.thoughtcrime.securesms.util

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Given a [ZoneId] return the time offset as a [ZoneOffset].
 */
fun ZoneId.toOffset(): ZoneOffset {
  return OffsetDateTime.now(this).offset
}

/**
 * Convert [LocalDateTime] to be same as [System.currentTimeMillis]
 */
fun LocalDateTime.toMillis(zoneOffset: ZoneOffset = ZoneId.systemDefault().toOffset()): Long {
  return TimeUnit.SECONDS.toMillis(toEpochSecond(zoneOffset))
}

/**
 * Return true if the [LocalDateTime] is within [start] and [end] inclusive.
 */
fun LocalDateTime.isBetween(start: LocalDateTime, end: LocalDateTime): Boolean {
  return (isEqual(start) || isAfter(start)) && (isEqual(end) || isBefore(end))
}

/**
 * Convert milliseconds to local date time with provided [zoneId].
 */
fun Long.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zoneId)
}

/**
 * Converts milliseconds to local time with provided [zoneId].
 */
fun Long.toLocalTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalTime {
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zoneId).toLocalTime()
}

/**
 * Formats [LocalTime] as localized time. For example, "8:00 AM"
 */
fun LocalTime.formatHours(context: Context): String {
  return if (Build.VERSION.SDK_INT >= 26 || !DateFormat.is24HourFormat(context)) {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(this)
  } else {
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(this)
  }
}

/**
 * Get the days of the week in order based on [Locale].
 */
fun Locale.orderOfDaysInWeek(): List<DayOfWeek> {
  val firstDayOfWeek: DayOfWeek = WeekFields.of(this).firstDayOfWeek
  return listOf(
    firstDayOfWeek,
    firstDayOfWeek.plus(1),
    firstDayOfWeek.plus(2),
    firstDayOfWeek.plus(3),
    firstDayOfWeek.plus(4),
    firstDayOfWeek.plus(5),
    firstDayOfWeek.plus(6),
  )
}
