package org.thoughtcrime.securesms.lock;


import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;

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
    long lastReminderTime = SignalStore.pin().getLastSuccessfulEntryTime();
    long nextIntervalTime = SignalStore.pin().getNextReminderInterval();

    return System.currentTimeMillis() > lastReminderTime + nextIntervalTime;
  }

  public static void scheduleReminder(@NonNull Context context, boolean success) {
    if (success) {
      long timeSinceLastReminder = System.currentTimeMillis() - SignalStore.pin().getLastSuccessfulEntryTime();
      Long nextReminderInterval = INTERVALS.higher(timeSinceLastReminder);

      if (nextReminderInterval == null) {
        nextReminderInterval = INTERVALS.last();
      }

      SignalStore.pin().setLastSuccessfulEntryTime(System.currentTimeMillis());
      SignalStore.pin().setNextReminderInterval(nextReminderInterval);
    } else {
      long timeSinceLastReminder = SignalStore.pin().getLastSuccessfulEntryTime() + TimeUnit.MINUTES.toMillis(5);
      SignalStore.pin().setLastSuccessfulEntryTime(timeSinceLastReminder);
    }
  }
}
