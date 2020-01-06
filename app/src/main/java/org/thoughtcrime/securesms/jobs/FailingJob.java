package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
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
  public @NonNull Data serialize() {
    return Data.EMPTY;
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
  public void onCanceled() {
  }

  public static final class Factory implements Job.Factory<FailingJob> {
    @Override
    public @NonNull FailingJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new FailingJob(parameters);
    }
  }
}
