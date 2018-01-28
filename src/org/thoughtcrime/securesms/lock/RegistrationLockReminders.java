package org.thoughtcrime.securesms.lock;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RegistrationLockReminders {

  public static final long INITIAL_INTERVAL = TimeUnit.HOURS.toMillis(6);

  private static final Map<Long, Long> INTERVAL_PROGRESSION = new HashMap<Long, Long>();
  static {
    INTERVAL_PROGRESSION.put(TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(12));
    INTERVAL_PROGRESSION.put(TimeUnit.HOURS.toMillis(12), TimeUnit.DAYS.toMillis(1));
    INTERVAL_PROGRESSION.put(TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(3));
    INTERVAL_PROGRESSION.put(TimeUnit.DAYS.toMillis(3), TimeUnit.DAYS.toMillis(7));
    INTERVAL_PROGRESSION.put(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(7));
  }


  private static final Map<Long, Long> INTERVAL_REGRESSION = new HashMap<Long, Long>();
  static {
    INTERVAL_REGRESSION.put(TimeUnit.HOURS.toMillis(12), TimeUnit.HOURS.toMillis(6));
    INTERVAL_REGRESSION.put(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(12));
    INTERVAL_REGRESSION.put(TimeUnit.DAYS.toMillis(3), TimeUnit.DAYS.toMillis(1));
    INTERVAL_REGRESSION.put(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(3));
  }


  public static boolean needsReminder(@NonNull Context context) {
    if (!TextSecurePreferences.isRegistrationtLockEnabled(context)) return false;

    long lastReminderTime = TextSecurePreferences.getRegistrationLockLastReminderTime(context);
    long nextIntervalTime = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);

    return System.currentTimeMillis() > lastReminderTime + nextIntervalTime;
  }

  public static void scheduleReminder(@NonNull Context context, boolean success) {
    long lastReminderInterval     = TextSecurePreferences.getRegistrationLockNextReminderInterval(context);
    long nextReminderInterval;

    if (success) {
      if (INTERVAL_PROGRESSION.containsKey(lastReminderInterval)) nextReminderInterval = INTERVAL_PROGRESSION.get(lastReminderInterval);
      else                                                        nextReminderInterval = INTERVAL_PROGRESSION.get(TimeUnit.HOURS.toMillis(6));
    } else {
      if (INTERVAL_REGRESSION.containsKey(lastReminderInterval))  nextReminderInterval = INTERVAL_REGRESSION.get(lastReminderInterval);
      else                                                        nextReminderInterval = INTERVAL_REGRESSION.get(TimeUnit.HOURS.toMillis(12));
    }

    TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
    TextSecurePreferences.setRegistrationLockNextReminderInterval(context, nextReminderInterval);
  }

}
