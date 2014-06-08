/**
 * Copyright (C) 2014 Open WhisperSystems
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
    final String prettyDate;
    if (isToday(millis)) {
      prettyDate = DateUtils.formatDateTime(c, millis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_TIME);
    } else if (isWithinWeek(millis)) {
      prettyDate = DateUtils.formatDateTime(c, millis,
          DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_TIME);
    } else if (isWithinYear(millis)) {
      prettyDate = DateUtils.formatDateTime(c, millis,
          DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL);
    } else {
      prettyDate = DateUtils.formatDateTime(c, millis,
          DateUtils.FORMAT_NUMERIC_DATE);
    }
    return prettyDate;
  }
}
