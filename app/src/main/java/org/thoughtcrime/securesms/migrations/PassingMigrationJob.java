package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.jobmanager.Job;

/**
 * A migration job that always passes. Not useful on it's own, but you can register it's factory for jobs that
 * have been removed that you'd like to pass instead of keeping around.
 */
public final class PassingMigrationJob extends MigrationJob {

  public static final String KEY = "PassingMigrationJob";

  private PassingMigrationJob(Parameters parameters) {
    super(parameters);
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    // Nothing
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  public static final class Factory implements Job.Factory<PassingMigrationJob> {
    @Override
    public @NonNull PassingMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new PassingMigrationJob(parameters);
    }
  }
}
