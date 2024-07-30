package org.thoughtcrime.securesms.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.app.AlarmManagerCompat;
import androidx.core.content.ContextCompat;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.whispersystems.signalservice.api.util.SleepTimer;

import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A sleep timer that is based on elapsed realtime, so that it works properly, even in low-power sleep modes.
 */
public class AlarmSleepTimer implements SleepTimer {
  private static final String TAG = Log.tag(AlarmSleepTimer.class);

  private static final ConcurrentSkipListSet<Integer> actionIdList = new ConcurrentSkipListSet<>();

  private final Context context;

  public AlarmSleepTimer(Context context) {
    this.context = context;
  }

  @Override
  public void sleep(long sleepDuration) {
    AlarmReceiver alarmReceiver = new AlarmSleepTimer.AlarmReceiver();
    int           actionId      = 0;

    while (!actionIdList.add(actionId)){
      actionId++;
    }

    try {
      String actionName = buildActionName(actionId);
      ContextCompat.registerReceiver(context, alarmReceiver, new IntentFilter(actionName), ContextCompat.RECEIVER_NOT_EXPORTED);

      long startTime = System.currentTimeMillis();
      alarmReceiver.setAlarm(sleepDuration, actionName);

      while (System.currentTimeMillis() - startTime < sleepDuration) {
        try {
          synchronized (this) {
            wait(sleepDuration - (System.currentTimeMillis() - startTime));
          }
        } catch (InterruptedException e) {
          Log.w(TAG, e);
        }
      }
      context.unregisterReceiver(alarmReceiver);
    } catch(Exception e) {
      Log.w(TAG, "Exception during sleep ...",e);
    } finally {
      actionIdList.remove(actionId);
    }
  }

  private static String buildActionName(int actionId) {
    return AlarmReceiver.WAKE_UP_THREAD_ACTION + "." + actionId;
  }

  private class AlarmReceiver extends BroadcastReceiver {
    private static final String WAKE_UP_THREAD_ACTION = "org.thoughtcrime.securesms.util.AlarmSleepTimer.AlarmReceiver.WAKE_UP_THREAD";

    private void setAlarm(long millis, String action) {
      final Intent intent = new Intent(action);
      intent.setPackage(context.getPackageName());

      final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntentFlags.mutable());
      final AlarmManager  alarmManager  = ServiceUtil.getAlarmManager(context);

      if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
        Log.d(TAG, "Setting an exact alarm to wake up in " + millis + "ms.");
        AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager,
                                                     AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                                     SystemClock.elapsedRealtime() + millis,
                                                     pendingIntent);
      } else {
        Log.w(TAG, "Setting an inexact alarm to wake up in " + millis + "ms. CanScheduleAlarms: " + alarmManager.canScheduleExactAlarms());
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                          SystemClock.elapsedRealtime() + millis,
                                          pendingIntent);
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "Waking up.");

      synchronized (AlarmSleepTimer.this) {
        AlarmSleepTimer.this.notifyAll();
      }
    }
  }
}

