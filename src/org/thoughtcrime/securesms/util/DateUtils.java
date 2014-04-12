package org.thoughtcrime.securesms.util;

import android.content.Context;

import java.util.Calendar;

/**
 * Created by kaonashi on 1/6/14.
 */
public class DateUtils extends android.text.format.DateUtils {

  private final static long DAY_IN_MILLIS  = 86400000L;
  private final static long WEEK_IN_MILLIS = 7 * DAY_IN_MILLIS;
  private final static long YEAR_IN_MILLIS = (long)(52.1775 * WEEK_IN_MILLIS);

  private static boolean isWithinWeek(final long millis) {
    return System.currentTimeMillis() - millis <= (WEEK_IN_MILLIS - DAY_IN_MILLIS);
  }

  private static boolean isWithinYear(final long millis) {
    return System.currentTimeMillis() - millis <= YEAR_IN_MILLIS;
  }

  public static String getBetterRelativeTimeSpanString(final Context c, final long millis) {
    int formatFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_TIME;
    if (!isToday(millis)) {
      if (isWithinWeek(millis)) {
        formatFlags = formatFlags | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
      } else if (isWithinYear(millis)) {
        formatFlags = formatFlags | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
      } else {
        formatFlags = formatFlags | DateUtils.FORMAT_NUMERIC_DATE;
      }
    }
    return DateUtils.formatDateTime(c, millis, formatFlags);
  }
}
