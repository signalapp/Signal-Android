package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class CachedAttachmentsMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(CachedAttachmentsMigrationJob.class);

  public static final String KEY = "CachedAttachmentsMigrationJob";

  CachedAttachmentsMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private CachedAttachmentsMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() {
    File externalCacheDir = context.getExternalCacheDir();

    if (externalCacheDir == null || !externalCacheDir.exists() || !externalCacheDir.isDirectory()) {
      Log.w(TAG, "External Cache Directory either does not exist or isn't a directory. Skipping.");
      return;
    }

    try {
      FileUtils.deleteDirectoryContents(context.getExternalCacheDir());
      GlideApp.get(context).clearDiskCache();
    } catch (IOException e) {
      Log.w(TAG, e);
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

  public static class Factory implements Job.Factory<CachedAttachmentsMigrationJob> {
    @Override
    public @NonNull CachedAttachmentsMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new CachedAttachmentsMigrationJob(parameters);
    }
  }
}
