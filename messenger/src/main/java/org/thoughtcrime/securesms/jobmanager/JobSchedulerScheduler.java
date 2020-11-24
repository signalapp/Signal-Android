package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.logging.Log;

import java.util.List;

@RequiresApi(26)
public class JobSchedulerScheduler implements Scheduler {

  private static final String TAG = JobSchedulerScheduler.class.getSimpleName();

  private static final String PREF_NAME    = "JobSchedulerScheduler_prefs";
  private static final String PREF_NEXT_ID = "pref_next_id";

  private static final int MAX_ID = 75;

  private final Application application;

  JobSchedulerScheduler(@NonNull Application application) {
    this.application = application;
  }

  @RequiresApi(26)
  @Override
  public void schedule(long delay, @NonNull List<Constraint> constraints) {
    JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(getNextId(), new ComponentName(application, SystemService.class))
                                                .setMinimumLatency(delay)
                                                .setPersisted(true);

    for (Constraint constraint : constraints) {
      constraint.applyToJobInfo(jobInfoBuilder);
    }

    Log.i(TAG, "Scheduling a run in " + delay + " ms.");
    JobScheduler jobScheduler = application.getSystemService(JobScheduler.class);
    jobScheduler.schedule(jobInfoBuilder.build());
  }

  private int getNextId() {
    SharedPreferences prefs      = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    int               returnedId = prefs.getInt(PREF_NEXT_ID, 0);
    int               nextId     = returnedId + 1 > MAX_ID ? 0 : returnedId + 1;

    prefs.edit().putInt(PREF_NEXT_ID, nextId).apply();

    return returnedId;
  }

  @RequiresApi(api = 26)
  public static class SystemService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
      Log.d(TAG, "onStartJob()");

      JobManager jobManager = ApplicationContext.getInstance(getApplicationContext()).getJobManager();

      jobManager.addOnEmptyQueueListener(new JobManager.EmptyQueueListener() {
        @Override
        public void onQueueEmpty() {
          jobManager.removeOnEmptyQueueListener(this);
          jobFinished(params, false);
          Log.d(TAG, "jobFinished()");
        }
      });

      jobManager.wakeUp();

      return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
      Log.d(TAG, "onStopJob()");
      return false;
    }
  }
}
