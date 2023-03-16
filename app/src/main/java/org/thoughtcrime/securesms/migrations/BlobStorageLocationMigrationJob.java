package org.thoughtcrime.securesms.migrations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Job;

import java.io.File;

/**
 * We moved files stored by {@link org.thoughtcrime.securesms.providers.BlobProvider} from the cache
 * into internal storage, so we gotta move any existing multi-session files.
 */
public class BlobStorageLocationMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(BlobStorageLocationMigrationJob.class);

  public static final String KEY = "BlobStorageLocationMigrationJob";

  BlobStorageLocationMigrationJob() {
    this(new Job.Parameters.Builder().build());
  }

  private BlobStorageLocationMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    File oldDirectory = new File(context.getCacheDir(), "multi_session_blobs");

    File[] oldFiles = oldDirectory.listFiles();

    if (oldFiles == null) {
      Log.i(TAG, "No files to move.");
      return;
    }

    Log.i(TAG, "Preparing to move " + oldFiles.length + " files.");

    File newDirectory = context.getDir("multi_session_blobs", Context.MODE_PRIVATE);

    for (File oldFile : oldFiles) {
      if (oldFile.renameTo(new File(newDirectory, oldFile.getName()))) {
        Log.i(TAG, "Successfully moved file: " + oldFile.getName());
      } else {
        Log.w(TAG, "Failed to move file! " + oldFile.getAbsolutePath());
      }
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  public static class Factory implements Job.Factory<BlobStorageLocationMigrationJob> {

    @Override
    public @NonNull BlobStorageLocationMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new BlobStorageLocationMigrationJob(parameters);
    }
  }
}
