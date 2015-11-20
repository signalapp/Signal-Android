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
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Locale;

import org.thoughtcrime.securesms.R;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
public class DateUtils extends android.text.format.DateUtils {
  private static boolean isWithin(final long millis, final long span, final TimeUnit unit) {
    return System.currentTimeMillis() - millis <= unit.toMillis(span);
  }

  private static boolean isYesterday(final long when) {
    return DateUtils.isToday(when + TimeUnit.DAYS.toMillis(1));
  }

  private static int convertDelta(final long millis, TimeUnit to) {
    return (int)to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS);
  }

  private static String getFormattedDateTime(long time, String template, Locale locale) {
    final String localizedPattern = getLocalizedPattern(template, locale);
    return new SimpleDateFormat(localizedPattern, locale).format(new Date(time));
  }

  public static String getRelativeTime(final Context c, final Locale locale,
                                       final long timestamp)
  {
    if (isWithin(timestamp, 1, TimeUnit.MINUTES)) {
      return c.getString(R.string.DateUtils_now);
    } else if (isWithin(timestamp, 1, TimeUnit.HOURS)) {
      int mins = convertDelta(timestamp, TimeUnit.MINUTES);
      return c.getResources().getString(R.string.DateUtils_minutes_ago, mins);
    } else if (isWithin(timestamp, 1, TimeUnit.DAYS)) {
      int hours = convertDelta(timestamp, TimeUnit.HOURS);
      return c.getResources().getQuantityString(R.plurals.hours_ago, hours, hours);
    } else {
      if (DateFormat.is24HourFormat(c)) return getFormattedDateTime(timestamp, "HH:mm", locale);
      else                              return getFormattedDateTime(timestamp, "hh:mm a", locale);
    }
  }

  public static String getRelativeDatetime(final Context c, final Locale locale, final long timestamp) {
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

  public static String getRelativeDate(@NonNull Context context,
                                       @NonNull Locale locale,
                                       long timestamp)
  {
    if (isToday(timestamp)) {
      return context.getString(R.string.DateUtils_today);
    } else if (isYesterday(timestamp)) {
      return context.getString(R.string.DateUtils_yesterday);
    } else {
      return getFormattedDateTime(timestamp, "EEE, MMM d, yyyy", locale);
    }
  }

  public static SimpleDateFormat getDetailedDateFormatter(Context context, Locale locale) {
    String dateFormatPattern;

    if (DateFormat.is24HourFormat(context)) {
      dateFormatPattern = getLocalizedPattern("MMM d, yyyy HH:mm:ss zzz", locale);
    } else {
      dateFormatPattern = getLocalizedPattern("MMM d, yyyy hh:mm:ss a zzz", locale);
    }

    return new SimpleDateFormat(dateFormatPattern, locale);
  }

  private static String getLocalizedPattern(String template, Locale locale) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return DateFormat.getBestDateTimePattern(locale, template);
    } else {
      return new SimpleDateFormat(template, locale).toLocalizedPattern();
    }
  }
}
