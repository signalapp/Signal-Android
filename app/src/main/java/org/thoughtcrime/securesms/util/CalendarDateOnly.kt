package org.thoughtcrime.securesms.util

import java.util.Calendar

object CalendarDateOnly {
  @JvmStatic
  fun getInstance(): Calendar {
    return Calendar.getInstance().apply {
      removeTime(this)
    }
  }

  @JvmStatic
  fun removeTime(calendar: Calendar) {
    calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY))
    calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE))
    calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND))
    calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND))
  }
}
