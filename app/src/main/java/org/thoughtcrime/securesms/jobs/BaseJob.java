package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager.Chain;
import org.thoughtcrime.securesms.logging.Log;

public abstract class BaseJob extends Job {

  private static final String TAG = BaseJob.class.getSimpleName();

  private Data outputData;

  public BaseJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Result run() {
    try {
      onRun();
      return Result.success(outputData);
    } catch (RuntimeException e) {
      Log.e(TAG, "Encountered a fatal exception. Crash imminent.", e);
      return Result.fatalFailure(e);
    } catch (Exception e) {
      if (onShouldRetry(e)) {
        Log.i(TAG, JobLogger.format(this, "Encountered a retryable exception."), e);
        return Result.retry();
      } else {
        Log.w(TAG, JobLogger.format(this, "Encountered a failing exception."), e);
        return Result.failure();
      }
    }
  }

  protected abstract void onRun() throws Exception;

  protected abstract boolean onShouldRetry(@NonNull Exception e);

  /**
   * If this job is part of a {@link Chain}, data set here will be passed as input data to the next
   * job(s) in the chain.
   */
  protected void setOutputData(@Nullable Data outputData) {
    this.outputData = outputData;
  }

  protected void log(@NonNull String tag, @NonNull String message) {
    Log.i(tag, JobLogger.format(this, message));
  }

  protected void warn(@NonNull String tag, @NonNull String message) {
    warn(tag, message, null);
  }

  protected void warn(@NonNull String tag, @Nullable Throwable t) {
    warn(tag, "", t);
  }

  protected void warn(@NonNull String tag, @NonNull String message, @Nullable Throwable t) {
    Log.w(tag, JobLogger.format(this, message), t);
  }
}
