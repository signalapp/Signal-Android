package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceStorageSyncRequestJob;
import org.thoughtcrime.securesms.jobs.StorageForcePushJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class StorageKeyRotationMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(StorageKeyRotationMigrationJob.class);

  public static final String KEY = "StorageKeyRotationMigrationJob";

  StorageKeyRotationMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private StorageKeyRotationMigrationJob(@NonNull Parameters parameters) {
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
    JobManager jobManager = ApplicationDependencies.getJobManager();
    SignalStore.storageServiceValues().rotateStorageMasterKey();

    if (TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Multi-device.");
      jobManager.startChain(new StorageForcePushJob())
                .then(new MultiDeviceKeysUpdateJob())
                .then(new MultiDeviceStorageSyncRequestJob())
                .enqueue();
    } else {
      Log.i(TAG, "Single-device.");
      jobManager.add(new StorageForcePushJob());
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<StorageKeyRotationMigrationJob> {
    @Override
    public @NonNull StorageKeyRotationMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StorageKeyRotationMigrationJob(parameters);
    }
  }
}
