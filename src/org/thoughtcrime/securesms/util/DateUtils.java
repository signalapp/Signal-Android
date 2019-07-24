/*
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

import androidx.annotation.NonNull;
import android.text.format.DateFormat;

import org.thoughtcrime.securesms.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
public class DateUtils extends android.text.format.DateUtils {

  @SuppressWarnings("unused")
  private static final String           TAG         = DateUtils.class.getSimpleName();
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

  private static boolean isWithin(final long millis, final long span, final TimeUnit unit) {
    return System.currentTimeMillis() - millis <= unit.toMillis(span);
  }

  private static boolean isYesterday(final long when) {
    return DateUtils.isToday(when + TimeUnit.DAYS.toMillis(1));
  }

  private static int convertDelta(final long millis, TimeUnit to) {
    return (int) to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS);
  }

  private static String getFormattedDateTime(long time, String template, Locale locale) {
    final String localizedPattern = getLocalizedPattern(template, locale);
    return new SimpleDateFormat(localizedPattern, locale).format(new Date(time));
  }

  public static String getBriefRelativeTimeSpanString(final Context c, final Locale locale, final long timestamp) {
    if (isWithin(timestamp, 1, TimeUnit.MINUTES)) {
      return c.getString(R.string.DateUtils_just_now);
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
      return c.getString(R.string.DateUtils_just_now);
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

  public static String getDayPrecisionTimeSpanString(Context context, Locale locale, long timestamp) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");

    if (simpleDateFormat.format(System.currentTimeMillis()).equals(simpleDateFormat.format(timestamp))) {
      return context.getString(R.string.DeviceListItem_today);
    } else {
      String format;

      if      (isWithin(timestamp, 6, TimeUnit.DAYS))   format = "EEE ";
      else if (isWithin(timestamp, 365, TimeUnit.DAYS)) format = "MMM d";
      else                                              format = "MMM d, yyy";

      return getFormattedDateTime(timestamp, format, locale);
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

  public static boolean isSameDay(long t1, long t2) {
    return DATE_FORMAT.format(new Date(t1)).equals(DATE_FORMAT.format(new Date(t2)));
  }

  public static boolean isSameExtendedRelativeTimestamp(@NonNull Context context, @NonNull Locale locale, long t1, long t2) {
    return getExtendedRelativeTimeSpanString(context, locale, t1).equals(getExtendedRelativeTimeSpanString(context, locale, t2));
  }

  private static String getLocalizedPattern(String template, Locale locale) {
    return DateFormat.getBestDateTimePattern(locale, template);
  }
}
