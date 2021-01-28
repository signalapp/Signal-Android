package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager.Chain;

public abstract class BaseJob extends Job {

  private static final String TAG = BaseJob.class.getSimpleName();

  private Data outputData;

  public BaseJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Result run() {
    if (shouldTrace()) {
      Tracer.getInstance().start(getClass().getSimpleName());
    }

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
    } finally {
      if (shouldTrace()) {
        Tracer.getInstance().end(getClass().getSimpleName());
      }
    }
  }

  protected abstract void onRun() throws Exception;

  protected abstract boolean onShouldRetry(@NonNull Exception e);

  /**
   * Whether or not the job should be traced with the {@link org.signal.core.util.tracing.Tracer}.
   */
  protected boolean shouldTrace() {
    return false;
  }

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

  protected void log(@NonNull String tag, @NonNull String extra, @NonNull String message) {
    Log.i(tag, JobLogger.format(this, extra, message));
  }

  protected void warn(@NonNull String tag, @NonNull String message) {
    warn(tag, "", message, null);
  }

  protected void warn(@NonNull String tag, @NonNull String event, @NonNull String message) {
    warn(tag, event, message, null);
  }

  protected void warn(@NonNull String tag, @Nullable Throwable t) {
    warn(tag, "", t);
  }

  protected void warn(@NonNull String tag, @NonNull String message, @Nullable Throwable t) {
    warn(tag, "", message, t);
  }

  protected void warn(@NonNull String tag, @NonNull String extra, @NonNull String message, @Nullable Throwable t) {
    Log.w(tag, JobLogger.format(this, extra, message), t);
  }
}
