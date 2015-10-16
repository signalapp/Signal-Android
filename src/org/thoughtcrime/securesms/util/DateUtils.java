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

import org.thoughtcrime.securesms.R;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
public class DateUtils extends android.text.format.DateUtils {

  private static final long JANUARY_FIRST_MILLIS;

  static {
    Calendar c = GregorianCalendar.getInstance();
    c.set(Calendar.MONTH, Calendar.JANUARY);
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    JANUARY_FIRST_MILLIS = c.getTimeInMillis();
  }

  private static boolean isWithin(final long millis, final long span, final TimeUnit unit) {
    return System.currentTimeMillis() - millis <= unit.toMillis(span);
  }

  private static int convertDelta(final long millis, TimeUnit to) {
    return (int) to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS);
  }

  private static String getFormattedDateTime(long time, String template, Locale locale) {
    String localizedPattern = new SimpleDateFormat(template, locale).toLocalizedPattern();
    return new SimpleDateFormat(localizedPattern, locale).format(new Date(time));
  }

  public static String getBriefRelativeTimeSpanString(final Context c, final Locale locale, final long timestamp) {
    if (isWithin(timestamp, 1, TimeUnit.MINUTES)) {
      return c.getString(R.string.DateUtils_now);
    } else if (isWithin(timestamp, 1, TimeUnit.HOURS)) {
      int mins = convertDelta(timestamp, TimeUnit.MINUTES);
      return c.getResources().getString(R.string.DateUtils_minutes_ago, mins);
    } else if (isWithin(timestamp, 1, TimeUnit.DAYS)) {
      int hours = convertDelta(timestamp, TimeUnit.HOURS);
      return c.getResources().getQuantityString(R.plurals.hours_ago, hours, hours);
    } else if (isWithin(timestamp, 6, TimeUnit.DAYS)) {
      return getFormattedDateTime(timestamp, "EEE", locale);
    } else if (isWithin(timestamp, 365, TimeUnit.DAYS)) {
      return getFormattedDateTime(timestamp, "MMM d", locale);
    } else {
      return getFormattedDateTime(timestamp, "MMM d, yyyy", locale);
    }
  }

  public static String getExtendedRelativeTimeSpanString(final Context c, final Locale locale, final long timestamp) {
    if (isWithin(timestamp, 1, TimeUnit.MINUTES)) {
      return c.getString(R.string.DateUtils_now);
    } else if (isWithin(timestamp, 1, TimeUnit.HOURS)) {
      int mins = (int)TimeUnit.MINUTES.convert(System.currentTimeMillis() - timestamp, TimeUnit.MILLISECONDS);
      return c.getResources().getString(R.string.DateUtils_minutes_ago, mins);
    } else {
      StringBuilder format = new StringBuilder();
      if      (isWithin(timestamp,   6, TimeUnit.DAYS)) format.append("EEE ");
      else if (isWithin(timestamp, 365, TimeUnit.DAYS)) format.append("MMM d, ");
      else                                              format.append("MMM d, yyyy, ");

      if (DateFormat.is24HourFormat(c)) format.append("HH:mm");
      else                              format.append("hh:mm a");

      return getFormattedDateTime(timestamp, format.toString(), locale);
    }
  }

  public static SimpleDateFormat getDetailedDateFormatter(Context context, Locale locale) {
    String dateFormatPattern;

    if (DateFormat.is24HourFormat(context)) {
      dateFormatPattern = "MMM d, yyyy HH:mm:ss zzz";
    } else {
      dateFormatPattern = "MMM d, yyyy hh:mm:ss a zzz";
    }

    return new SimpleDateFormat(dateFormatPattern, locale);
  }

  public static String getDateSeparatorString(final Locale l, final long timestamp) {
    String format;
    if (timestamp < JANUARY_FIRST_MILLIS) format = "MMM d, yyyy";
    else                                  format = "MMM d";

    return getFormattedDateTime(timestamp, format, l);
  }

}
