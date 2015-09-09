/*
 * Copyright (C) 2013 Open Whisper Systems
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

package org.thoughtcrime.redphone.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Random;

/**
 * Helpers for periodically scheduling actions to random intervals
 *
 * @author Stuart O. Anderson
 */
public class PeriodicActionUtils {

  private PeriodicActionUtils() {
    //util
  }

  public static <T extends BroadcastReceiver> void scheduleUpdate(Context context, Class<T> clazz) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    AlarmManager am               = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    Intent intent                 = new Intent(context, clazz);
    PendingIntent sender          = PendingIntent.getBroadcast(context, 0, intent,
                                                               PendingIntent.FLAG_UPDATE_CURRENT);
    Random random                 = new Random(System.currentTimeMillis());
    long offset                   = random.nextLong() % (12 * 60 * 60 * 1000);
    long interval                 = (24 * 60 * 60 * 1000) + offset;
    String prefKey                = "pref_scheduled_monitor_config_update_" + clazz.getCanonicalName();
    long scheduledTime            = preferences.getLong(prefKey, -1);

    if (scheduledTime == -1 ) {
      context.sendBroadcast(intent);
    }

    if (scheduledTime <= System.currentTimeMillis()) {
      context.sendBroadcast(intent);
      scheduledTime = System.currentTimeMillis() + interval;
      preferences.edit().putLong(prefKey, scheduledTime).commit();
      Log.w("PeriodicActionUtils", "Scheduling for all new time: " + scheduledTime
          + " (" + clazz.getSimpleName() + ")");
    } else {
      Log.w("PeriodicActionUtils", "Scheduling for time found in preferences: " + scheduledTime
          + " (" + clazz.getSimpleName() + ")");
    }

    am.cancel(sender);
    am.set(AlarmManager.RTC_WAKEUP, scheduledTime, sender);

    Log.w("PeriodicActionUtils", "Scheduled for: " + scheduledTime
        + " (" + clazz.getSimpleName() + ")");
  }
}
