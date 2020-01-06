package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import android.os.PowerManager;
import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.WakeLockUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A thread that constantly checks for available {@link Job}s owned by the {@link JobController}.
 * When one is available, this class will execute it and call the appropriate methods on
 * {@link JobController} based on the result.
 *
 * {@link JobRunner} and {@link JobController} were written such that you should be able to have
 * N concurrent {@link JobRunner}s operating over the same {@link JobController}.
 */
class JobRunner extends Thread {

  private static final String TAG = JobRunner.class.getSimpleName();

  private static long WAKE_LOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(10);

  private final Application   application;
  private final int           id;
  private final JobController jobController;

  JobRunner(@NonNull Application application, int id, @NonNull JobController jobController) {
    super("signal-JobRunner-" + id);

    this.application   = application;
    this.id            = id;
    this.jobController = jobController;
  }

  @Override
  public synchronized void run() {
    //noinspection InfiniteLoopStatement
    while (true) {
      Job        job    = jobController.pullNextEligibleJobForExecution();
      Job.Result result = run(job);

      jobController.onJobFinished(job);

      if (result.isSuccess()) {
        jobController.onSuccess(job);
      } else if (result.isRetry()) {
        jobController.onRetry(job);
        job.onRetry();
      } else if (result.isFailure()) {
        List<Job> dependents = jobController.onFailure(job);
        job.onCanceled();
        Stream.of(dependents).forEach(Job::onCanceled);

        if (result.getException() != null) {
          throw result.getException();
        }
      } else {
        throw new AssertionError("Invalid job result!");
      }
    }
  }

  private Job.Result run(@NonNull Job job) {
    Log.i(TAG, JobLogger.format(job, String.valueOf(id), "Running job."));

    if (isJobExpired(job)) {
      Log.w(TAG, JobLogger.format(job, String.valueOf(id), "Failing after surpassing its lifespan."));
      return Job.Result.failure();
    }

    Job.Result            result   = null;
    PowerManager.WakeLock wakeLock = null;

    try {
      wakeLock = WakeLockUtil.acquire(application, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TIMEOUT, job.getId());
      result = job.run();
    } catch (Exception e) {
      Log.w(TAG, JobLogger.format(job, String.valueOf(id), "Failing due to an unexpected exception."), e);
      return Job.Result.failure();
    } finally {
      if (wakeLock != null) {
        WakeLockUtil.release(wakeLock, job.getId());
      }
    }

    printResult(job, result);

    if (result.isRetry()                                                &&
        job.getRunAttempt() + 1 >= job.getParameters().getMaxAttempts() &&
        job.getParameters().getMaxAttempts() != Job.Parameters.UNLIMITED)
    {
      Log.w(TAG, JobLogger.format(job, String.valueOf(id), "Failing after surpassing its max number of attempts."));
      return Job.Result.failure();
    }

    return result;
  }

  private boolean isJobExpired(@NonNull Job job) {
    long expirationTime = job.getParameters().getCreateTime() + job.getParameters().getLifespan();

    if (expirationTime < 0) {
      expirationTime = Long.MAX_VALUE;
    }

    return job.getParameters().getLifespan() != Job.Parameters.IMMORTAL && expirationTime <= System.currentTimeMillis();
  }

  private void printResult(@NonNull Job job, @NonNull Job.Result result) {
    if (result.getException() != null) {
      Log.e(TAG, JobLogger.format(job, String.valueOf(id), "Job failed with a fatal exception. Crash imminent."));
    } else if (result.isFailure()) {
      Log.w(TAG, JobLogger.format(job, String.valueOf(id), "Job failed."));
    } else {
      Log.i(TAG, JobLogger.format(job, String.valueOf(id), "Job finished with result: " + result));
    }
  }
}
