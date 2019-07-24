package org.thoughtcrime.securesms.jobmanager;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.logging.Log;

import java.util.List;
import java.util.UUID;

/**
 * Schedules tasks using the {@link AlarmManager}.
 *
 * Given that this scheduler is only used when {@link KeepAliveService} is also used (which keeps
 * all of the {@link ConstraintObserver}s running), this only needs to schedule future runs in
 * situations where all constraints are already met. Otherwise, the {@link ConstraintObserver}s will
 * trigger future runs when the constraints are met.
 *
 * For the same reason, this class also doesn't have to schedule jobs that don't have delays.
 *
 * Important: Only use on API < 26.
 */
public class AlarmManagerScheduler implements Scheduler {

  private static final String TAG = AlarmManagerScheduler.class.getSimpleName();

  private final Application application;

  AlarmManagerScheduler(@NonNull Application application) {
    this.application = application;
  }

  @Override
  public void schedule(long delay, @NonNull List<Constraint> constraints) {
    if (delay > 0 && Stream.of(constraints).allMatch(Constraint::isMet)) {
      setUniqueAlarm(application, System.currentTimeMillis() + delay);
    }
  }

  private void setUniqueAlarm(@NonNull Context context, long time) {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       intent       = new Intent(context, RetryReceiver.class);

    intent.setAction(BuildConfig.APPLICATION_ID + UUID.randomUUID().toString());
    alarmManager.set(AlarmManager.RTC_WAKEUP, time, PendingIntent.getBroadcast(context, 0, intent, 0));

    Log.i(TAG, "Set an alarm to retry a job in " + (time - System.currentTimeMillis()) + " ms.");
  }

  public static class RetryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.i(TAG, "Received an alarm to retry a job.");
      ApplicationContext.getInstance(context).getJobManager().wakeUp();
    }
  }
}
