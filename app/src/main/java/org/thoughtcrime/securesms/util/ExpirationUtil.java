package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public final class ExpirationUtil {

  private static final int SECONDS_IN_WEEK   = (int) TimeUnit.DAYS.toSeconds(7);
  private static final int SECONDS_IN_DAY    = (int) TimeUnit.DAYS.toSeconds(1);
  private static final int SECONDS_IN_HOUR   = (int) TimeUnit.HOURS.toSeconds(1);
  private static final int SECONDS_IN_MINUTE = (int) TimeUnit.MINUTES.toSeconds(1);

  public static String getExpirationDisplayValue(Context context, int expirationTime) {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off);
    }

    String displayValue = "";

    int secondsRemaining = expirationTime;

    int weeks = secondsRemaining / SECONDS_IN_WEEK;
    displayValue     = getDisplayValue(context, displayValue, R.plurals.expiration_weeks, weeks);
    secondsRemaining = secondsRemaining - weeks * SECONDS_IN_WEEK;

    int days = secondsRemaining / SECONDS_IN_DAY;
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_days, days);
    if (weeks > 0) {
      return displayValue;
    }
    secondsRemaining = secondsRemaining - days * SECONDS_IN_DAY;

    int hours = secondsRemaining / SECONDS_IN_HOUR;
    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_hours, hours);
    if (days > 0) {
      return displayValue;
    }
    secondsRemaining = secondsRemaining - hours * SECONDS_IN_HOUR;

    int minutes = secondsRemaining / SECONDS_IN_MINUTE;
    displayValue     = getDisplayValue(context, displayValue, R.plurals.expiration_minutes, minutes);
    if (hours > 0) {
      return displayValue;
    }
    secondsRemaining = secondsRemaining - minutes * SECONDS_IN_MINUTE;

    displayValue = getDisplayValue(context, displayValue, R.plurals.expiration_seconds, secondsRemaining);

    return displayValue;
  }

  private static String getDisplayValue(Context context, String currentValue, @PluralsRes int plurals, int duration) {
    if (duration > 0) {
      String durationString = context.getResources().getQuantityString(plurals, duration, duration);
      if (currentValue.isEmpty()) {
        return durationString;
      } else {
        return context.getString(R.string.expiration_combined, currentValue, durationString);
      }
    }
    return currentValue;
  }

  public static String getExpirationAbbreviatedDisplayValue(Context context, int expirationTime) {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off);
    }

    String displayValue = "";

    int secondsRemaining = expirationTime;

    int weeks = secondsRemaining / SECONDS_IN_WEEK;
    displayValue     = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_weeks_abbreviated, weeks);
    secondsRemaining = secondsRemaining - weeks * SECONDS_IN_WEEK;

    int days = secondsRemaining / SECONDS_IN_DAY;
    displayValue     = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_days_abbreviated, days);
    secondsRemaining = secondsRemaining - days * SECONDS_IN_DAY;

    int hours = secondsRemaining / SECONDS_IN_HOUR;
    displayValue     = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_hours_abbreviated, hours);
    secondsRemaining = secondsRemaining - hours * SECONDS_IN_HOUR;

    int minutes = secondsRemaining / SECONDS_IN_MINUTE;
    displayValue     = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_minutes_abbreviated, minutes);
    secondsRemaining = secondsRemaining - minutes * SECONDS_IN_MINUTE;

    displayValue = getAbbreviatedDisplayValue(context, displayValue, R.string.expiration_seconds_abbreviated, secondsRemaining);

    return displayValue;
  }

  private static String getAbbreviatedDisplayValue(Context context, String currentValue, @StringRes int abbreviation, int duration) {
    if (duration > 0) {
      String durationString = context.getString(abbreviation, duration);
      if (currentValue.isEmpty()) {
        return durationString;
      } else {
        return context.getString(R.string.expiration_combined, currentValue, durationString);
      }
    }
    return currentValue;
  }
}
