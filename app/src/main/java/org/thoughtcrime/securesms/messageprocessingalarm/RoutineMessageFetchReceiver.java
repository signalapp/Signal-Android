package org.thoughtcrime.securesms.messageprocessingalarm;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JobTracker;
import org.thoughtcrime.securesms.jobs.MessageFetchJob;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.RemoteConfig;

import java.util.Locale;
import java.util.Optional;

/**
 * A receiver that is scheduled via alarm to run at a remotely-configurable interval to fetch messages from the service.
 * Basically an extra reliability thing that helps poke the system to fetch messages more often.
 */
public final class RoutineMessageFetchReceiver extends BroadcastReceiver {

  private static final String TAG = Log.tag(RoutineMessageFetchReceiver.class);

  public static final String BROADCAST_ACTION = "org.thoughtcrime.securesms.action.PROCESS_MESSAGES";

  @Override
  @SuppressLint("StaticFieldLeak")
  public void onReceive(@NonNull Context context, @NonNull Intent intent) {
    Log.i(TAG, String.format("onReceive(%s)", intent.getAction()));

    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      Log.i(TAG, "Starting Alarm because of boot receiver");
      startOrUpdateAlarm(context);
    } else if (BROADCAST_ACTION.equals(intent.getAction())) {

      if (AppForegroundObserver.isForegrounded()) {
        Log.i(TAG, "App is foregrounded");
        return;
      }

      long foregroundDelayMs = RemoteConfig.getBackgroundMessageProcessForegroundDelay();
      long jobTimeout        = foregroundDelayMs + 200;

      Log.i(TAG, String.format(Locale.US, "Starting PushNotificationReceiveJob asynchronously with %d delay before foreground shown", foregroundDelayMs));

      PendingResult pendingResult = goAsync();

      new Handler(Looper.getMainLooper()).postDelayed(pendingResult::finish, jobTimeout);

      SignalExecutors.BOUNDED.submit(() -> {
        Log.i(TAG, "Running PushNotificationReceiveJob");

        Optional<JobTracker.JobState> jobState = AppDependencies.getJobManager()
                                                                .runSynchronously(new MessageFetchJob(), jobTimeout);

        Log.i(TAG, "PushNotificationReceiveJob ended: " + (jobState.isPresent() ? jobState.get().toString() : "Job did not complete"));
      });
    }
  }

  public static void startOrUpdateAlarm(@NonNull Context context) {
    Intent alarmIntent = new Intent(context, RoutineMessageFetchReceiver.class);

    alarmIntent.setAction(RoutineMessageFetchReceiver.BROADCAST_ACTION);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 123, alarmIntent, PendingIntentFlags.updateCurrent());
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

    long interval = RemoteConfig.getBackgroundMessageProcessInterval();

    if (interval < 0) {
      alarmManager.cancel(pendingIntent);
      Log.i(TAG, "Alarm cancelled");
    } else {
      alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + interval,
                                interval,
                                pendingIntent);
      Log.i(TAG, "Alarm scheduled to repeat at interval " + interval);
    }
  }
}
