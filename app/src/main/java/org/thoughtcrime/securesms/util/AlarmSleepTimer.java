package org.thoughtcrime.securesms.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ConcurrentSkipListSet;

import org.whispersystems.signalservice.api.util.SleepTimer;

/**
 * A sleep timer that is based on elapsed realtime, so
 * that it works properly, even in low-power sleep modes.
 *
 */
public class AlarmSleepTimer implements SleepTimer {
  private static final String TAG = AlarmSleepTimer.class.getSimpleName();
  private static ConcurrentSkipListSet<Integer> actionIdList = new ConcurrentSkipListSet<>();

  private final Context context;

  public AlarmSleepTimer(Context context) {
    this.context = context;
  }

  @Override
  public void sleep(long millis) {
    final AlarmReceiver alarmReceiver = new AlarmSleepTimer.AlarmReceiver();
    int actionId = 0;
    while (!actionIdList.add(actionId)){
      actionId++;
    }
    try {
      context.registerReceiver(alarmReceiver,
             new IntentFilter(AlarmReceiver.WAKE_UP_THREAD_ACTION + "." + actionId));

      final long startTime = System.currentTimeMillis();
      alarmReceiver.setAlarm(millis, AlarmReceiver.WAKE_UP_THREAD_ACTION + "." + actionId);

      while (System.currentTimeMillis() - startTime < millis) {
        try {
          synchronized (this) {
            wait(millis - System.currentTimeMillis() + startTime);
          }
        } catch (InterruptedException e) {
          Log.w(TAG, e);
        }
      }
      context.unregisterReceiver(alarmReceiver);
    } catch(Exception e) {
      Log.w(TAG, "Exception during sleep ...",e);
    }finally {
      actionIdList.remove(actionId);
    }
  }

  private class AlarmReceiver extends BroadcastReceiver {
    private static final String WAKE_UP_THREAD_ACTION = "org.thoughtcrime.securesms.util.AlarmSleepTimer.AlarmReceiver.WAKE_UP_THREAD";

    private void setAlarm(long millis, String action) {
      final Intent        intent        = new Intent(action);
      final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
      final AlarmManager  alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

      Log.w(TAG, "Setting alarm to wake up in " + millis + "ms.");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                               SystemClock.elapsedRealtime() + millis,
                                               pendingIntent);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                              SystemClock.elapsedRealtime() + millis,
                              pendingIntent);
      } else {
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
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

