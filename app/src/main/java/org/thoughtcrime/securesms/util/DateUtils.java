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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods to help display dates in a nice, easily readable way.
 */
public class DateUtils extends android.text.format.DateUtils {

  @SuppressWarnings("unused")
  private static final String                        TAG                = DateUtils.class.getSimpleName();
  private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT        = new ThreadLocal<>();
  private static final ThreadLocal<SimpleDateFormat> BRIEF_EXACT_FORMAT = new ThreadLocal<>();

  private static boolean isWithin(final long millis, final long span, final TimeUnit unit) {
    return System.currentTimeMillis() - millis <= unit.toMillis(span);
  }

  private static boolean isWithinAbs(final long millis, final long span, final TimeUnit unit) {
    return Math.abs(System.currentTimeMillis() - millis) <= unit.toMillis(span);
  }

  private static boolean isYesterday(final long when) {
    return DateUtils.isToday(when + TimeUnit.DAYS.toMillis(1));
  }

  private static int convertDelta(final long millis, TimeUnit to) {
    return (int) to.convert(System.currentTimeMillis() - millis, TimeUnit.MILLISECONDS);
  }

  private static String getFormattedDateTime(long time, String template, Locale locale) {
    final String localizedPattern = getLocalizedPattern(template, locale);
    return setLowercaseAmPmStrings(new SimpleDateFormat(localizedPattern, locale), locale).format(new Date(time));
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

  public static String getTimeString(final Context c, final Locale locale, final long timestamp) {
    StringBuilder format = new StringBuilder();

    if      (isSameDay(System.currentTimeMillis(), timestamp)) format.append("");
    else if (isWithinAbs(timestamp,   6, TimeUnit.DAYS))       format.append("EEE ");
    else if (isWithinAbs(timestamp, 364, TimeUnit.DAYS))       format.append("MMM d, ");
    else                                                       format.append("MMM d, yyyy, ");

    if (DateFormat.is24HourFormat(c)) format.append("HH:mm");
    else                              format.append("hh:mm a");

    return getFormattedDateTime(timestamp, format.toString(), locale);
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
      return formatDate(locale, timestamp);
    }
  }

  public static String formatDate(@NonNull Locale locale, long timestamp) {
    return getFormattedDateTime(timestamp, "EEE, MMM d, yyyy", locale);
  }

  public static String formatDateWithoutDayOfWeek(@NonNull Locale locale, long timestamp) {
    return getFormattedDateTime(timestamp, "MMM d yyyy", locale);
  }

  public static boolean isSameDay(long t1, long t2) {
    String d1 = getDateFormat().format(new Date(t1));
    String d2 = getDateFormat().format(new Date(t2));

    return d1.equals(d2);
  }

  public static boolean isSameExtendedRelativeTimestamp(@NonNull Context context, @NonNull Locale locale, long t1, long t2) {
    return getExtendedRelativeTimeSpanString(context, locale, t1).equals(getExtendedRelativeTimeSpanString(context, locale, t2));
  }

  private static String getLocalizedPattern(String template, Locale locale) {
    return DateFormat.getBestDateTimePattern(locale, template);
  }

  private static @NonNull SimpleDateFormat setLowercaseAmPmStrings(@NonNull SimpleDateFormat format, @NonNull Locale locale) {
    DateFormatSymbols symbols = new DateFormatSymbols(locale);

    symbols.setAmPmStrings(new String[] { "am", "pm"});
    format.setDateFormatSymbols(symbols);

    return format;
  }

  /**
   * e.g. 2020-09-04T19:17:51Z
   * https://www.iso.org/iso-8601-date-and-time-format.html
   *
   * Note: SDK_INT == 0 check needed to pass unit tests due to JVM date parser differences.
   *
   * @return The timestamp if able to be parsed, otherwise -1.
   */
  @SuppressLint("ObsoleteSdkInt")
  public static long parseIso8601(@Nullable String date) {
    SimpleDateFormat format;
    if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= 24) {
      format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault());
    } else {
      format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    }

    if (Util.isEmpty(date)) {
      return -1;
    }

    try {
      return format.parse(date).getTime();
    } catch (ParseException e) {
      Log.w(TAG, "Failed to parse date.", e);
      return -1;
    }
  }

  @SuppressLint("SimpleDateFormat")
  private static SimpleDateFormat getDateFormat() {
    SimpleDateFormat format = DATE_FORMAT.get();

    if (format == null) {
      format = new SimpleDateFormat("yyyyMMdd");
      DATE_FORMAT.set(format);
    }

    return format;
  }

  @SuppressLint("SimpleDateFormat")
  private static SimpleDateFormat getBriefExactFormat() {
    SimpleDateFormat format = BRIEF_EXACT_FORMAT.get();

    if (format == null) {
      format = new SimpleDateFormat();
      BRIEF_EXACT_FORMAT.set(format);
    }

    return format;
  }
}
