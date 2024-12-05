package org.thoughtcrime.securesms.notifications.profiles

import org.thoughtcrime.securesms.util.isBetween
import org.thoughtcrime.securesms.util.toLocalDateTime
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Encapsulate when a notification should be active based on days of the week, start time,
 * and end times.
 *
 * Examples:
 *
 *   start: 9am  end: 5pm  daysEnabled: Monday would return true for times between Monday 9am and Monday 5pm
 *   start: 9pm  end: 5am  daysEnabled: Monday would return true for times between Monday 9pm and Tuesday 5am
 *   start: 12am end: 12am daysEnabled: Monday would return true for times between Monday 12am and Monday 11:59:59pm
 */
data class NotificationProfileSchedule(
  val id: Long,
  val enabled: Boolean = false,
  val start: Int = 900,
  val end: Int = 1700,
  val daysEnabled: Set<DayOfWeek> = emptySet()
) {

  @JvmOverloads
  fun isCurrentlyActive(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (!enabled) {
      return false
    }
    return coversTime(now, zoneId)
  }

  @JvmOverloads
  fun coversTime(time: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    val localNow: LocalDateTime = time.toLocalDateTime(zoneId)
    val localStart: LocalDateTime = start.toLocalDateTime(localNow)
    val localEnd: LocalDateTime = end.toLocalDateTime(localNow)

    return if (end <= start) {
      (daysEnabled.contains(localStart.dayOfWeek.minus(1)) && localNow.isBetween(localStart.minusDays(1), localEnd)) || (daysEnabled.contains(localStart.dayOfWeek) && localNow.isBetween(localStart, localEnd.plusDays(1)))
    } else {
      daysEnabled.contains(localStart.dayOfWeek) && localNow.isBetween(localStart, localEnd)
    }
  }

  fun startTime(): LocalTime {
    return LocalTime.of(start / 100, start % 100)
  }

  fun startDateTime(localNow: LocalDateTime): LocalDateTime {
    val localStart: LocalDateTime = start.toLocalDateTime(localNow)
    val localEnd: LocalDateTime = end.toLocalDateTime(localNow)

    return if (end <= start && (daysEnabled.contains(localStart.dayOfWeek.minus(1)) && localNow.isBetween(localStart.minusDays(1), localEnd))) {
      localStart.minusDays(1)
    } else {
      localStart
    }
  }

  fun endTime(): LocalTime {
    return LocalTime.of(end / 100, end % 100)
  }

  fun endDateTime(localNow: LocalDateTime): LocalDateTime {
    val localStart: LocalDateTime = start.toLocalDateTime(localNow)
    val localEnd: LocalDateTime = end.toLocalDateTime(localNow)

    return if (end <= start && (daysEnabled.contains(localStart.dayOfWeek) && localNow.isBetween(localStart, localEnd.plusDays(1)))) {
      localEnd.plusDays(1)
    } else {
      localEnd
    }
  }
}

fun Int.toLocalDateTime(now: LocalDateTime): LocalDateTime {
  return now.withHour(this / 100).withMinute(this % 100).withSecond(0)
}
