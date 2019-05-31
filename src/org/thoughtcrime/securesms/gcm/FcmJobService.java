package org.thoughtcrime.securesms.gcm;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;

import javax.inject.Inject;

/**
 * Pulls down messages. Used when we fail to pull down messages in {@link FcmService}.
 */
@RequiresApi(26)
public class FcmJobService extends JobService implements InjectableType {

  private static final String TAG = FcmJobService.class.getSimpleName();

  private static final int ID = 1337;

  @Inject SignalServiceMessageReceiver messageReceiver;

  @RequiresApi(26)
  public static void schedule(@NonNull Context context) {
    JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(ID, new ComponentName(context, FcmJobService.class))
                                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                                .setBackoffCriteria(0, JobInfo.BACKOFF_POLICY_LINEAR)
                                                .setPersisted(true);

    ServiceUtil.getJobScheduler(context).schedule(jobInfoBuilder.build());
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    Log.d(TAG, "onStartJob()");
    ApplicationContext.getInstance(getApplicationContext()).injectDependencies(this);

    if (ApplicationContext.getInstance(getApplicationContext()).isAppVisible()) {
      Log.i(TAG, "App is foregrounded. No need to run.");
      return false;
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        new PushNotificationReceiveJob(getApplicationContext()).pullAndProcessMessages(messageReceiver, TAG, System.currentTimeMillis());
        jobFinished(params, false);
      } catch (IOException e) {
        Log.w(TAG, "Failed to pull. Notifying and scheduling a retry.", e);
        MessageNotifier.notifyMessagesPending(getApplicationContext());
        jobFinished(params, true);
      }
    });

    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Log.d(TAG, "onStopJob()");
    return TextSecurePreferences.getNeedsMessagePull(getApplicationContext());
  }
}
