/*
 * Copyright (C) 2017 Fernando Garcia Alvarez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to check if it's night or day
 *
 * @author fercarcedo
 */

public class TimeHelper {
  private static final int FRESHNESS_HOURS = 24;

  private static Calendar sunriseCalendar;
  private static Calendar sunsetCalendar;
  private static Calendar lastModifiedDate;

  public static boolean isNight(Context context) {
    calculateNightTimeIfNecessary(context);
    Calendar todayCalendar = Calendar.getInstance();
    return CalendarUtils.afterHoursAndMinutes(todayCalendar, sunsetCalendar)
            || CalendarUtils.beforeHoursAndMinutes(todayCalendar, sunriseCalendar);
  }

  private static boolean timeNotSet() {
    return sunriseCalendar == null || sunsetCalendar == null;
  }

  private static boolean shouldCalculateNightTime() {
    return timeNotSet() || freshnessTimeElapsed();
  }

  private static boolean freshnessTimeElapsed() {
    Calendar todayCalendar = Calendar.getInstance();
    long diffSeconds = (todayCalendar.getTimeInMillis() - lastModifiedDate.getTimeInMillis());
    long diffHours = TimeUnit.SECONDS.toHours(diffSeconds);

    return diffHours > FRESHNESS_HOURS;
  }

  private static void calculateNightTimeIfNecessary(Context context) {
    if (timeNotSet()) {
      loadNightTimeFromSharedPreferences(context);
    }

    if (shouldCalculateNightTime()) {
      calculateNightTime(context);
      storeNightTimeIntoSharedPreferences(context);
    }
  }

  private static void loadNightTimeFromSharedPreferences(Context context) {
    long sunriseTime = TextSecurePreferences.getSunriseTime(context);
    long sunsetTime = TextSecurePreferences.getSunsetTime(context);
    long lastModifiedTime = TextSecurePreferences.getSunsetLastModifiedTime(context);

    if (sunriseTime > 0 && sunsetTime > 0 && lastModifiedTime > 0) {
      sunriseCalendar = CalendarUtils.createFromMillis(sunriseTime);
      sunsetCalendar = CalendarUtils.createFromMillis(sunsetTime);
      lastModifiedDate = CalendarUtils.createFromMillis(lastModifiedTime);
    }
  }

  private static void storeNightTimeIntoSharedPreferences(Context context) {
    TextSecurePreferences.setSunriseTime(context, sunriseCalendar.getTimeInMillis());
    TextSecurePreferences.setSunsetTime(context, sunsetCalendar.getTimeInMillis());
    TextSecurePreferences.setSunsetLastModifiedTime(context, lastModifiedDate.getTimeInMillis());
  }

  private static void calculateNightTime(Context context) {
    Location location = getLastKnownLocation(context);

    if (location != null) {
      setNightTimeBasedOnLocation(location);
    } else {
      setDefaultNightTime();
    }
      lastModifiedDate = Calendar.getInstance();
  }

  private static void setDefaultNightTime() {
    Calendar todayCalendar = Calendar.getInstance();
    int currentYear = todayCalendar.get(Calendar.YEAR);
    int currentMonth = todayCalendar.get(Calendar.MONTH);
    int currentDay = todayCalendar.get(Calendar.DAY_OF_MONTH);

    sunsetCalendar = Calendar.getInstance();
    sunsetCalendar.set(currentYear, currentMonth, currentDay, 18, 0);

    sunriseCalendar = Calendar.getInstance();
    sunriseCalendar.set(currentYear, currentMonth, currentDay, 6, 0);
  }

  private static void setNightTimeBasedOnLocation(Location location) {
    SunriseSunsetCalculator sunriseSunsetCalculator = new SunriseSunsetCalculator(
            new com.luckycatlabs.sunrisesunset.dto.Location(location.getLatitude(), location.getLongitude()),
            TimeZone.getDefault().getID());

    Calendar todayCalendar = Calendar.getInstance();
    sunsetCalendar = sunriseSunsetCalculator.getOfficialSunsetCalendarForDate(todayCalendar);
    sunriseCalendar = sunriseSunsetCalculator.getOfficialSunriseCalendarForDate(todayCalendar);
  }

  private static Location getLastKnownLocation(Context context) {
    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    return locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
  }
}
