package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;

/**
 * A job that always fails. Not useful on it's own, but you can register it's factory for jobs that
 * have been removed that you'd like to fail instead of keeping around.
 */
public final class FailingJob extends Job {

  public static final String KEY = "FailingJob";

  private FailingJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @NonNull
  @Override
  public String getFactoryKey() {
    return KEY;
  }

  @Override
  public @NonNull Result run() {
    return Result.failure();
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<FailingJob> {
    @Override
    public @NonNull FailingJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new FailingJob(parameters);
    }
  }
}
