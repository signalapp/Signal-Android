package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;

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
  public @Nullable byte[] serialize() {
    return null;
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
    public @NonNull MarkerJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new MarkerJob(parameters);
    }
  }
}
