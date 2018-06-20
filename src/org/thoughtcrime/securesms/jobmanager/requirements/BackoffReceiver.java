package org.thoughtcrime.securesms.jobmanager.requirements;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;

import java.util.UUID;

public class BackoffReceiver extends BroadcastReceiver {

  private static final String TAG = BackoffReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Received an alarm to retry a job with ID: " + intent.getAction());
    ApplicationContext.getInstance(context).getJobManager().onRequirementStatusChanged();
  }

  public static void setUniqueAlarm(@NonNull Context context, long time) {
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent        intent        = new Intent(context, BackoffReceiver.class);

    intent.setAction(BuildConfig.APPLICATION_ID + UUID.randomUUID().toString());
    alarmManager.set(AlarmManager.RTC_WAKEUP, time, PendingIntent.getBroadcast(context, 0, intent, 0));

    Log.i(TAG, "Set an alarm to retry a job in " + (time - System.currentTimeMillis()) + " ms with ID: " + intent.getAction());
  }
}
