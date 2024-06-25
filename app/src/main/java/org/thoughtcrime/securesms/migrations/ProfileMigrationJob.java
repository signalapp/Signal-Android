package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;

/**
 * Schedules a re-upload of the users profile.
 */
public final class ProfileMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(ProfileMigrationJob.class);

  public static final String KEY = "ProfileMigrationJob";

  ProfileMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private ProfileMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    Log.i(TAG, "Scheduling profile upload job");
    AppDependencies.getJobManager().add(new ProfileUploadJob());
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<ProfileMigrationJob> {
    @Override
    public @NonNull ProfileMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ProfileMigrationJob(parameters);
    }
  }
}
