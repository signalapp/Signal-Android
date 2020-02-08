package org.thoughtcrime.securesms.lock;


import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public class RegistrationLockReminders {

  private static final NavigableSet<Long> INTERVALS = new TreeSet<Long>() {{
    add(TimeUnit.HOURS.toMillis(6));
    add(TimeUnit.HOURS.toMillis(12));
    add(TimeUnit.DAYS.toMillis(1));
    add(TimeUnit.DAYS.toMillis(3));
    add(TimeUnit.DAYS.toMillis(7));
  }};

  public static final long INITIAL_INTERVAL = INTERVALS.first();

  public static boolean needsReminder(@NonNull Context context) {
    long lastReminderTime = TextSecurePreferences.getRegistrationLockLastReminderTime(context);
    long nextIntervalTime = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);

    return System.currentTimeMillis() > lastReminderTime + nextIntervalTime;
  }

  public static void scheduleReminder(@NonNull Context context, boolean success) {
    if (success) {
      long timeSinceLastReminder = System.currentTimeMillis() - TextSecurePreferences.getRegistrationLockLastReminderTime(context);
      Long nextReminderInterval = INTERVALS.higher(timeSinceLastReminder);

      if (nextReminderInterval == null) {
        nextReminderInterval = INTERVALS.last();
      }

      TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
      TextSecurePreferences.setRegistrationLockNextReminderInterval(context, nextReminderInterval);
    } else {
      long timeSinceLastReminder = TextSecurePreferences.getRegistrationLockLastReminderTime(context) + TimeUnit.MINUTES.toMillis(5);
      TextSecurePreferences.setRegistrationLockLastReminderTime(context, timeSinceLastReminder);
    }
  }
}
