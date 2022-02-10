package org.thoughtcrime.securesms.jobs;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupFileIOError;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupExporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.util.BackupUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Backup Job for installs requiring Scoped Storage.
 *
 * @see LocalBackupJob#enqueue(boolean)
 */
public final class LocalChunkedBackupJobApi29 extends BaseJob {

  public static final String KEY = "LocalChunkedBackupJobApi29";

  private static final String TAG = Log.tag(LocalChunkedBackupJobApi29.class);

  public static final String TEMP_BACKUP_FILE_PREFIX = ".backup";
  public static final String TEMP_BACKUP_FILE_SUFFIX = ".tmp";

  LocalChunkedBackupJobApi29(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    Log.i(TAG, "Executing backup job...");

    BackupFileIOError.clearNotification(context);

    if (!BackupUtil.isUserSelectionRequired(context)) {
      throw new IOException("Wrong backup job!");
    }

    Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
    if (backupDirectoryUri == null || backupDirectoryUri.getPath() == null) {
      throw new IOException("Backup Directory has not been selected!");
    }

    try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
                                                                                            context.getString(R.string.LocalBackupJob_creating_signal_backup),
                                                                                            NotificationChannels.BACKUPS,
                                                                                            R.drawable.ic_signal_backup))
    {
      notification.setIndeterminateProgress();

      String       backupPassword  = BackupPassphrase.get(context);
      DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
      String       timestamp       = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
      String       fileName        = String.format("signal-%s.backup", timestamp);

      if (backupDirectory == null || !backupDirectory.canWrite()) {
        BackupFileIOError.ACCESS_ERROR.postNotification(context);
        throw new IOException("Cannot write to backup directory location.");
      }

      deleteOldTemporaryBackups(backupDirectory);

      if (backupDirectory.findFile(fileName) != null) {
        throw new IOException("Backup file already exists!");
      }

      String       temporaryPrefix = String.format(Locale.US, "%s%s", TEMP_BACKUP_FILE_PREFIX, UUID.randomUUID());

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      List<DocumentFile> tmpFiles = null;

      try {
        tmpFiles = FullBackupExporter.exportChunked(context,
                                                    AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                                    SignalDatabase.getBackupDatabase(),
                                                    backupDirectory,
                                                    temporaryPrefix,
                                                    TEMP_BACKUP_FILE_SUFFIX,
                                                    backupPassword,
                                                    this::isCanceled);
        List<String> outFiles = BackupUtil.generateBackupFilenames(tmpFiles.size());
        if (!BackupUtil.renameMulti2(tmpFiles, outFiles)) {
          Log.w(TAG, "Failed to rename temp files");
          throw new IOException("Renaming temporary backup files failed!");
        }
      } catch (FullBackupExporter.BackupCanceledException e) {
        Log.w(TAG, "Backup cancelled");
        throw e;
      } catch (IOException e) {
        Log.w(TAG, "Error during backup!", e);
        BackupFileIOError.postNotificationForException(context, e, getRunAttempt());
        throw e;
      } finally {
        if (tmpFiles != null) {
          for (DocumentFile fileToCleanUp : tmpFiles) {
            if (fileToCleanUp != null) {
              if (fileToCleanUp.delete()) {
                Log.w(TAG, "Backup failed. Deleted temp file");
              } else {
                Log.w(TAG, "Backup failed. Failed to delete temp file " + fileToCleanUp.getName());
              }
            }
          }
        }
      }

      BackupUtil.deleteOldChunkedBackups();
    }
  }

  private static void deleteOldTemporaryBackups(@NonNull DocumentFile backupDirectory) {
    for (DocumentFile file : backupDirectory.listFiles()) {
      if (file.isFile()) {
        String name = file.getName();
        if (name != null && name.startsWith(TEMP_BACKUP_FILE_PREFIX) && name.endsWith(TEMP_BACKUP_FILE_SUFFIX)) {
          if (file.delete()) {
            Log.w(TAG, "Deleted old temporary backup file");
          } else {
            Log.w(TAG, "Could not delete old temporary backup file");
          }
        }
      }
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<LocalChunkedBackupJobApi29> {
    @Override
    public @NonNull
    LocalChunkedBackupJobApi29 create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LocalChunkedBackupJobApi29(parameters);
    }
  }
}
