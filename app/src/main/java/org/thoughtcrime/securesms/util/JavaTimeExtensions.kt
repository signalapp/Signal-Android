package org.thoughtcrime.securesms.util

import android.content.Context
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
@JvmOverloads
fun LocalDateTime.toMillis(zoneOffset: ZoneOffset = ZoneId.systemDefault().toOffset()): Long {
  return TimeUnit.SECONDS.toMillis(toEpochSecond(zoneOffset))
}

/**
 * Convert [ZonedDateTime] to be same as [System.currentTimeMillis]
 */
fun ZonedDateTime.toMillis(): Long {
  return TimeUnit.SECONDS.toMillis(toEpochSecond())
}

/**
 * Convert [LocalDateTime] to a [ZonedDateTime] at the UTC offset
 */
fun LocalDateTime.atUTC(): ZonedDateTime {
  return atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC))
}

/**
 * Create a LocalDateTime with the same year, month, and day, but set
 * to midnight.
 */
fun LocalDateTime.atMidnight(): LocalDateTime {
  return LocalDateTime.of(year, month, dayOfMonth, 0, 0)
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
 * Convert milliseconds to local date with provided [zoneId].
 */
fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
  return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}

/**
 * Convert milliseconds to local date time with provided [zoneId].
 */
fun Instant.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
  return LocalDateTime.ofInstant(this, zoneId)
}

/**
 * Converts milliseconds to local time with provided [zoneId].
 */
fun Long.toLocalTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalTime {
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zoneId).toLocalTime()
}

/**
 * Formats [LocalTime] as localized time. For example, "1:45 PM" or "13:45"
 */
fun LocalTime.formatHours(context: Context): String {
  // We have to create our own pattern here, since the formatter instance returned by DateTimeFormatter.ofLocalizedTime() is looked up lazily, is immutable,
  // and is not updated when the system's 24-hour time setting changes.
  val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
  return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(this)
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
    firstDayOfWeek.plus(6)
  )
}
