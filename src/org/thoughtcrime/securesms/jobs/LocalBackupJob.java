package org.thoughtcrime.securesms.jobs;


import android.Manifest;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.FullBackupExporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class LocalBackupJob extends ContextJob {

  private static final String TAG = LocalBackupJob.class.getSimpleName();

  public LocalBackupJob(@NonNull Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("__LOCAL_BACKUP__")
                                .withWakeLock(true, 10, TimeUnit.SECONDS)
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws NoExternalStorageException, IOException {
    Log.w(TAG, "Executing backup job...");

    if (!Permissions.hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      throw new IOException("No external storage permission!");
    }

    GenericForegroundService.startForegroundTask(context,
                                                 context.getString(R.string.LocalBackupJob_creating_backup));

    try {
      String backupPassword  = TextSecurePreferences.getBackupPassphrase(context);
      File   backupDirectory = StorageUtil.getBackupDirectory(context);
      String timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
      String fileName        = String.format("signal-%s.backup", timestamp);
      File   backupFile      = new File(backupDirectory, fileName);

      if (backupFile.exists()) {
        throw new IOException("Backup file already exists?");
      }

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      File tempFile = File.createTempFile("backup", "tmp", context.getExternalCacheDir());

      FullBackupExporter.export(context,
                                AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                DatabaseFactory.getBackupDatabase(context),
                                tempFile,
                                backupPassword);

      if (!tempFile.renameTo(backupFile)) {
        tempFile.delete();
        throw new IOException("Renaming temporary backup file failed!");
      }

      BackupUtil.deleteOldBackups(context);
    } finally {
      GenericForegroundService.stopForegroundTask(context);
    }
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return false;
  }

  @Override
  public void onCanceled() {

  }
}
