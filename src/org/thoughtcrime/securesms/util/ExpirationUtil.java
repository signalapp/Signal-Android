package org.thoughtcrime.securesms.util;

import android.content.Context;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public class ExpirationUtil {

  public static String getExpirationDisplayValue(Context context, int expirationTime) {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off);
    } else if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
      return context.getResources().getQuantityString(R.plurals.expiration_seconds, expirationTime, expirationTime);
    } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
      int minutes = expirationTime / (int)TimeUnit.MINUTES.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_minutes, minutes, minutes);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
      int hours = expirationTime / (int)TimeUnit.HOURS.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_hours, hours, hours);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
      int days = expirationTime / (int)TimeUnit.DAYS.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_days, days, days);
    } else {
      int weeks = expirationTime / (int)TimeUnit.DAYS.toSeconds(7);
      return context.getResources().getQuantityString(R.plurals.expiration_weeks, weeks, weeks);
    }
  }

  public static String getExpirationAbbreviatedDisplayValue(Context context, int expirationTime) {
    if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
      return context.getResources().getString(R.string.expiration_seconds_abbreviated, expirationTime);
    } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
      int minutes = expirationTime / (int)TimeUnit.MINUTES.toSeconds(1);
      return context.getResources().getString(R.string.expiration_minutes_abbreviated, minutes);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
      int hours = expirationTime / (int)TimeUnit.HOURS.toSeconds(1);
      return context.getResources().getString(R.string.expiration_hours_abbreviated, hours);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
      int days = expirationTime / (int)TimeUnit.DAYS.toSeconds(1);
      return context.getResources().getString(R.string.expiration_days_abbreviated, days);
    } else {
      int weeks = expirationTime / (int)TimeUnit.DAYS.toSeconds(7);
      return context.getResources().getString(R.string.expiration_weeks_abbreviated, weeks);
    }
  }


}
