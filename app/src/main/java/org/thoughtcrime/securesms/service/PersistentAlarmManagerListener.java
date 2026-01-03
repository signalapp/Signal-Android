package org.thoughtcrime.securesms.service;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;

public abstract class PersistentAlarmManagerListener extends BroadcastReceiver {

  private static final String TAG             = Log.tag(PersistentAlarmManagerListener.class);
  private static final String ACTION_SCHEDULE = "signal.ACTION_SCHEDULE";

  protected static @NonNull Intent getScheduleIntent() {
    Intent scheduleIntent = new Intent();
    scheduleIntent.setAction(ACTION_SCHEDULE);
    return scheduleIntent;
  }

  protected abstract long getNextScheduledExecutionTime(Context context);

  protected abstract long onAlarm(Context context, long scheduledTime);

  public void cancel(Context context) {
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent alarmIntent = new Intent(context, getClass());
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntentFlags.immutable());

    info("Cancelling alarm");
    alarmManager.cancel(pendingIntent);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    info(String.format("onReceive(%s)", intent.getAction()));

    long          scheduledTime = getNextScheduledExecutionTime(context);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent        alarmIntent   = new Intent(context, getClass());
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntentFlags.immutable());

    if (System.currentTimeMillis() >= scheduledTime && canRunDuringSchedule(intent.getAction())) {
      info("onAlarm(): scheduled: " + scheduledTime + " actual: " + System.currentTimeMillis());
      scheduledTime = onAlarm(context, scheduledTime);
    }

    if (pendingIntent == null) {
      info("PendingIntent somehow null, skipping");
      return;
    }

    // If we've already scheduled this alarm, cancel it so we can schedule it again with the new time.
    alarmManager.cancel(pendingIntent);

    if (shouldScheduleExact()) {
      scheduleExact(alarmManager, scheduledTime, pendingIntent);
    } else {
      info("scheduling alarm for: " + scheduledTime);
      alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent);
    }
  }

  private boolean canRunDuringSchedule(@NonNull String action) {
    return !shouldScheduleExact() || !ACTION_SCHEDULE.equals(action);
  }

  private void scheduleExact(AlarmManager alarmManager, long scheduledTime, PendingIntent pendingIntent) {
    boolean hasManagerPermission = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();

    info("scheduling exact alarm for: " + scheduledTime + " hasManagerPermission: " + hasManagerPermission);
    if (hasManagerPermission) {
      try {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent);
        return;
      } catch (Exception e) {
        warn(e);
      }
    }

    warn("Unable to schedule exact alarm, falling back to inexact alarm, scheduling alarm for: " + scheduledTime);
    alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent);
  }

  protected boolean shouldScheduleExact() {
    return false;
  }

  private void info(String message) {
    Log.i(TAG, "[" + getClass().getSimpleName() + "] " + message);
  }

  private void warn(String message) {
    Log.w(TAG, "[" + getClass().getSimpleName() + "] " + message);
  }

  private void warn(Throwable t) {
    Log.w(TAG, "[" + getClass().getSimpleName() + "]", t);
  }
}
