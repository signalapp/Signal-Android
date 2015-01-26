/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Utility methods to help display dates in a nice, easily readable way.
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
        formatFlags |= DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
      } else if (isWithinYear(millis)) {
        formatFlags |= DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
      } else {
        formatFlags |= DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL;
      }
    }
    return DateUtils.formatDateTime(c, millis, formatFlags);
  }

  public static SimpleDateFormat getDetailedDateFormatter(Context context) {
    String dateFormatPattern;

    if (DateFormat.is24HourFormat(context)) {
      dateFormatPattern = "MMM d, yyyy HH:mm:ss zzz";
    } else {
      dateFormatPattern = "MMM d, yyyy hh:mm:ssa zzz";
    }

    return new SimpleDateFormat(dateFormatPattern, Locale.getDefault());
  }

}
