package org.thoughtcrime.securesms.service;


import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.JavaTimeExtensionsKt;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(1);

  @Override
  protected boolean scheduleExact() {
    return Build.VERSION.SDK_INT >= 31;
  }

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getNextBackupTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (SignalStore.settings().isBackupEnabled()) {
      LocalBackupJob.enqueue(scheduleExact());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (SignalStore.settings().isBackupEnabled()) {
      new LocalBackupListener().onReceive(context, new Intent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    long nextTime;

    if (Build.VERSION.SDK_INT < 31) {
      nextTime = System.currentTimeMillis() + INTERVAL;
    } else {
      LocalDateTime now  = LocalDateTime.now();
      LocalDateTime next = now.withHour(2).withMinute(0).withSecond(0);
      if (now.getHour() >= 2) {
        next = next.plusDays(1);
      }

      nextTime = JavaTimeExtensionsKt.toMillis(next);
    }

    TextSecurePreferences.setNextBackupTime(context, nextTime);

    return nextTime;
  }
}
