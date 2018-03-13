package org.thoughtcrime.securesms.lock;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class RegistrationLockReminders {

  public static final long INITIAL_INTERVAL = TimeUnit.HOURS.toMillis(6);

  private static NavigableMap<Long, Long> INTERVAL_PROGRESSION = new TreeMap<Long, Long>() {{
    put(TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(12));
    put(TimeUnit.HOURS.toMillis(12), TimeUnit.DAYS.toMillis(1));
    put(TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(3));
    put(TimeUnit.DAYS.toMillis(3), TimeUnit.DAYS.toMillis(7));
    put(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(7));
  }};


  private static NavigableMap<Long, Long> INTERVAL_REGRESSION = new TreeMap<Long, Long>() {{
    put(TimeUnit.HOURS.toMillis(12), TimeUnit.HOURS.toMillis(6));
    put(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(12));
    put(TimeUnit.DAYS.toMillis(3), TimeUnit.DAYS.toMillis(1));
    put(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(3));
  }};


  public static boolean needsReminder(@NonNull Context context) {
    if (!TextSecurePreferences.isRegistrationtLockEnabled(context)) return false;

    long lastReminderTime = TextSecurePreferences.getRegistrationLockLastReminderTime(context);
    long nextIntervalTime = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);

    return System.currentTimeMillis() > lastReminderTime + nextIntervalTime;
  }

  public static void scheduleReminder(@NonNull Context context, boolean success) {
    Entry<Long, Long> nextReminderIntervalEntry;

    if (success) {
      long timeSincelastReminder = System.currentTimeMillis() - TextSecurePreferences.getRegistrationLockLastReminderTime(context);
      nextReminderIntervalEntry = INTERVAL_PROGRESSION.floorEntry(timeSincelastReminder);
      if (nextReminderIntervalEntry == null) nextReminderIntervalEntry = INTERVAL_PROGRESSION.firstEntry();
    } else {
      long lastReminderInterval = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);
      nextReminderIntervalEntry = INTERVAL_REGRESSION.floorEntry(lastReminderInterval);
      if (nextReminderIntervalEntry == null) nextReminderIntervalEntry = INTERVAL_REGRESSION.firstEntry();
    }

    TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
    TextSecurePreferences.setRegistrationLockNextReminderInterval(context, nextReminderIntervalEntry.getValue());
  }

}
