package org.thoughtcrime.securesms.migrations;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.backup.BackupFileIOError;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.BackupUtil;

import java.io.IOException;
import java.util.Locale;

/**
 * Handles showing a notification if we think backups were unintentionally disabled.
 */
public final class BackupNotificationMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(BackupNotificationMigrationJob.class);

  public static final String KEY = "BackupNotificationMigrationJob";

  BackupNotificationMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private BackupNotificationMigrationJob(@NonNull Parameters parameters) {
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
    if (Build.VERSION.SDK_INT >= 29 && !SignalStore.settings().isBackupEnabled() && BackupUtil.hasBackupFiles(context)) {
      Log.w(TAG, "Stranded backup! Notifying.");
      BackupFileIOError.UNKNOWN.postNotification(context);
    } else {
      Log.w(TAG, String.format(Locale.US, "Does not meet criteria. API: %d, BackupsEnabled: %s, HasFiles: %s",
                               Build.VERSION.SDK_INT,
                               SignalStore.settings().isBackupEnabled(),
                               BackupUtil.hasBackupFiles(context)));
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  public static class Factory implements Job.Factory<BackupNotificationMigrationJob> {
    @Override
    public @NonNull BackupNotificationMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new BackupNotificationMigrationJob(parameters);
    }
  }
}
