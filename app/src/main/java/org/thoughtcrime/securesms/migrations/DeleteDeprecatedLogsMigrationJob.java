package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Job;

import java.io.File;

/**
 * We moved from storing logs in encrypted files to just storing them in an encrypted database. So we need to delete the leftover files.
 */
public class DeleteDeprecatedLogsMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(DeleteDeprecatedLogsMigrationJob.class);

  public static final String KEY = "DeleteDeprecatedLogsMigrationJob";

  public DeleteDeprecatedLogsMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private DeleteDeprecatedLogsMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    File logDir = new File(context.getCacheDir(), "log");
    if (logDir.exists()) {
      File[] files = logDir.listFiles();

      int count = 0;
      if (files != null) {
        for (File f : files) {
          count += f.delete() ? 1 : 0;
        }
      }

      if (!logDir.delete()) {
        Log.w(TAG, "Failed to delete log directory.");
      }

      Log.i(TAG, "Deleted " + count + " log files.");
    } else {
      Log.w(TAG, "Log directory does not exist.");
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<DeleteDeprecatedLogsMigrationJob> {
    @Override
    public @NonNull DeleteDeprecatedLogsMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new DeleteDeprecatedLogsMigrationJob(parameters);
    }
  }
}
