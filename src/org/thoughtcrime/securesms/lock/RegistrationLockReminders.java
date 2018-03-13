package org.thoughtcrime.securesms.lock;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RegistrationLockReminders {

  public static final long INITIAL_INTERVAL = TimeUnit.HOURS.toMillis(6);
  
  public static boolean needsReminder(@NonNull Context context) {
    if (!TextSecurePreferences.isRegistrationtLockEnabled(context)) return false;

    long lastReminderTime = TextSecurePreferences.getRegistrationLockLastReminderTime(context);
    long nextIntervalTime = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);

    return System.currentTimeMillis() > lastReminderTime + nextIntervalTime;
  }

  public static void scheduleReminder(@NonNull Context context, boolean success) {
    long lastReminderInterval = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);
    long nextReminderInterval;

    if (success) {
      if      (lastReminderInterval <= TimeUnit.HOURS.toMillis(6))  nextReminderInterval = TimeUnit.HOURS.toMillis(12);
      else if (lastReminderInterval <= TimeUnit.HOURS.toMillis(12)) nextReminderInterval = TimeUnit.DAYS.toMillis(1);
      else if (lastReminderInterval <= TimeUnit.DAYS.toMillis(1))   nextReminderInterval = TimeUnit.DAYS.toMillis(3);
      else if (lastReminderInterval <= TimeUnit.DAYS.toMillis(3))   nextReminderInterval = TimeUnit.DAYS.toMillis(7);
      else                                                                  nextReminderInterval = TimeUnit.DAYS.toMillis(7);
    } else {
      if      (lastReminderInterval >= TimeUnit.DAYS.toMillis(7)) nextReminderInterval = TimeUnit.DAYS.toMillis(3);
      else if (lastReminderInterval >= TimeUnit.DAYS.toMillis(3)) nextReminderInterval = TimeUnit.DAYS.toMillis(1);
      else if (lastReminderInterval >= TimeUnit.DAYS.toMillis(1)) nextReminderInterval = TimeUnit.HOURS.toMillis(12);
      else                                                                nextReminderInterval = TimeUnit.HOURS.toMillis(6);
    }

    TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
    TextSecurePreferences.setRegistrationLockNextReminderInterval(context, nextReminderInterval);
  }

}
