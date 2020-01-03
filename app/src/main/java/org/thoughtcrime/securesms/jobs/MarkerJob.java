package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;

/**
 * Useful for putting in a queue as a marker to know that previously enqueued jobs have been processed.
 * <p>
 * Does no work.
 */
public final class MarkerJob extends BaseJob {

  private static final String TAG = Log.tag(MarkerJob.class);

  public static final String KEY = "MarkerJob";

  public MarkerJob(@Nullable String queue) {
    this(new Parameters.Builder()
                       .setQueue(queue)
                       .build());
  }

  private MarkerJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  protected void onRun() {
    Log.i(TAG, String.format("Marker reached in %s queue", getParameters().getQueue()));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<MarkerJob> {
    @Override
    public @NonNull MarkerJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MarkerJob(parameters);
    }
  }
}
