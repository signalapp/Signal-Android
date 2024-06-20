package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.IOException;

/**
 * Does a full directory refresh.
 */
public final class DirectoryRefreshMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(DirectoryRefreshMigrationJob.class);

  public static final String KEY = "DirectoryRefreshMigrationJob";

  DirectoryRefreshMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private DirectoryRefreshMigrationJob(@NonNull Parameters parameters) {
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
  public void performMigration() throws IOException {
    if (!SignalStore.account().isRegistered() ||
        !SignalStore.registration().isRegistrationComplete() ||
        SignalStore.account().getAci() == null)
    {
      Log.w(TAG, "Not registered! Skipping.");
      return;
    }

    ContactDiscovery.refreshAll(context, true);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  public static class Factory implements Job.Factory<DirectoryRefreshMigrationJob> {
    @Override
    public @NonNull DirectoryRefreshMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new DirectoryRefreshMigrationJob(parameters);
    }
  }
}
