package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

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
    if (!SignalStore.account().isRegistered()                      ||
        !SignalStore.registrationValues().isRegistrationComplete() ||
        SignalStore.account().getAci() == null)
    {
      Log.w(TAG, "Not registered! Skipping.");
      return;
    }

    DirectoryHelper.refreshDirectory(context, true);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  public static class Factory implements Job.Factory<DirectoryRefreshMigrationJob> {
    @Override
    public @NonNull DirectoryRefreshMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new DirectoryRefreshMigrationJob(parameters);
    }
  }
}
