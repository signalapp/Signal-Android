package org.thoughtcrime.securesms.service;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.signal.core.util.logging.Log;

public abstract class PersistentAlarmManagerListener extends BroadcastReceiver {

  private static final String TAG = Log.tag(PersistentAlarmManagerListener.class);

  protected abstract long getNextScheduledExecutionTime(Context context);
  protected abstract long onAlarm(Context context, long scheduledTime);

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, String.format("%s#onReceive(%s)", getClass().getSimpleName(), intent.getAction()));

    long          scheduledTime = getNextScheduledExecutionTime(context);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent        alarmIntent   = new Intent(context, getClass());
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);

    if (System.currentTimeMillis() >= scheduledTime) {
      scheduledTime = onAlarm(context, scheduledTime);
    }

    Log.i(TAG, getClass() + " scheduling for: " + scheduledTime + " action: " + intent.getAction());

    if (pendingIntent != null) {
      alarmManager.cancel(pendingIntent);
      alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent);
    } else {
      Log.i(TAG, "PendingIntent somehow null, skipping");
    }
  }
}
