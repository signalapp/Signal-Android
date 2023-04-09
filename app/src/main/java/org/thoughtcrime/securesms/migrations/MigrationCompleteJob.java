package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.BaseJob;

/**
 * A job that should be enqueued last in a series of migrations. When this runs, we know that the
 * current set of migrations has been completed.
 *
 * To avoid confusion around the possibility of multiples of these jobs being enqueued as the
 * result of doing multiple migrations, we associate the canonicalVersionCode with the job and
 * include that in the event we broadcast out.
 */
public class MigrationCompleteJob extends BaseJob {

  public static final String KEY = "MigrationCompleteJob";

  private final static String KEY_VERSION = "version";

  private final int version;

  MigrationCompleteJob(int version) {
    this(new Parameters.Builder()
                       .setQueue(Parameters.MIGRATION_QUEUE_KEY)
                       .setLifespan(Parameters.IMMORTAL)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
        version);
  }

  private MigrationCompleteJob(@NonNull Job.Parameters parameters, int version) {
    super(parameters);
    this.version = version;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putInt(KEY_VERSION, version).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    throw new AssertionError("This job should never fail.");
  }

  @Override
  protected void onRun() throws Exception {
    EventBus.getDefault().postSticky(new MigrationCompleteEvent(version));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  public static class Factory implements Job.Factory<MigrationCompleteJob> {
    @Override
    public @NonNull MigrationCompleteJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new MigrationCompleteJob(parameters, data.getInt(KEY_VERSION));
    }
  }
}
