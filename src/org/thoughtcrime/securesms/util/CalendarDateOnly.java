package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import java.util.Calendar;

public final class CalendarDateOnly {

  public static Calendar getInstance() {
    Calendar calendar = Calendar.getInstance();

    removeTime(calendar);

    return calendar;
  }

  public static void removeTime(@NonNull Calendar calendar) {
    calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY));
    calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE));
    calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND));
    calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND));
  }
}
