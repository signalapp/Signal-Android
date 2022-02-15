package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob;

/**
 * Schedules a emoji download job to get the latest version.
 */
public final class EmojiDownloadMigrationJob extends MigrationJob {

  public static final String KEY = "EmojiDownloadMigrationJob";

  EmojiDownloadMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private EmojiDownloadMigrationJob(@NonNull Parameters parameters) {
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
    ApplicationDependencies.getJobManager().add(new DownloadLatestEmojiDataJob(false));
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<EmojiDownloadMigrationJob> {
    @Override
    public @NonNull EmojiDownloadMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new EmojiDownloadMigrationJob(parameters);
    }
  }
}
