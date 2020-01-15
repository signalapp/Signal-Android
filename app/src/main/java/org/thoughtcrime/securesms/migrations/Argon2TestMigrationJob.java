package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.Argon2TestJob;

/**
 * Triggers a Argon2 Test, just once.
 */
public final class Argon2TestMigrationJob extends MigrationJob {

  public static final String KEY = "Argon2TestMigrationJob";

  private Argon2TestMigrationJob(Parameters parameters) {
    super(parameters);
  }

  public Argon2TestMigrationJob() {
    this(new Parameters.Builder().build());
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    ApplicationDependencies.getJobManager().add(new Argon2TestJob());
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  public static final class Factory implements Job.Factory<Argon2TestMigrationJob> {
    @Override
    public @NonNull Argon2TestMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new Argon2TestMigrationJob(parameters);
    }
  }
}
