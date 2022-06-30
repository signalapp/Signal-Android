package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;

/**
 * Marks all distribution lists as needing to be synced with storage service.
 */
public final class SyncDistributionListsMigrationJob extends MigrationJob {

  public static final String KEY = "SyncDistributionListsMigrationJob";

  SyncDistributionListsMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private SyncDistributionListsMigrationJob(@NonNull Parameters parameters) {
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
    SignalDatabase.recipients().markNeedsSync(SignalDatabase.distributionLists().getAllListRecipients());
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<SyncDistributionListsMigrationJob> {
    @Override
    public @NonNull SyncDistributionListsMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SyncDistributionListsMigrationJob(parameters);
    }
  }
}
