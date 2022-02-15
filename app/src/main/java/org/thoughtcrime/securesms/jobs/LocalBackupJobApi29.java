package org.thoughtcrime.securesms.jobs;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupFileIOError;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupBase;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Backup Job for installs requiring Scoped Storage.
 *
 * @see LocalBackupJob#enqueue(boolean)
 */
public final class LocalBackupJobApi29 extends BaseJob {

  public static final String KEY = "LocalBackupJobApi29";

  private static final String TAG = Log.tag(LocalBackupJobApi29.class);

  public static final String TEMP_BACKUP_FILE_PREFIX = ".backup";
  public static final String TEMP_BACKUP_FILE_SUFFIX = ".tmp";

  LocalBackupJobApi29(@NonNull Parameters parameters) {
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

    ProgressUpdater updater = new ProgressUpdater();
    try (NotificationController notification = GenericForegroundService.startForegroundTask(context,
                                                                                            context.getString(R.string.LocalBackupJob_creating_signal_backup),
                                                                                            NotificationChannels.BACKUPS,
                                                                                            R.drawable.ic_signal_backup))
    {
      updater.setNotification(notification);
      EventBus.getDefault().register(updater);
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

      String       temporaryName = String.format(Locale.US, "%s%s%s", TEMP_BACKUP_FILE_PREFIX, UUID.randomUUID(), TEMP_BACKUP_FILE_SUFFIX);
      DocumentFile temporaryFile = backupDirectory.createFile("application/octet-stream", temporaryName);

      if (temporaryFile == null) {
        throw new IOException("Failed to create temporary backup file.");
      }

      if (backupPassword == null) {
        throw new IOException("Backup password is null");
      }

      try {
        FullBackupExporter.export(context,
                                  AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                  SignalDatabase.getBackupDatabase(),
                                  temporaryFile,
                                  backupPassword,
                                  this::isCanceled);

        if (!temporaryFile.renameTo(fileName)) {
          Log.w(TAG, "Failed to rename temp file");
          throw new IOException("Renaming temporary backup file failed!");
        }
      } catch (FullBackupExporter.BackupCanceledException e) {
        Log.w(TAG, "Backup cancelled");
        throw e;
      } catch (IOException e) {
        Log.w(TAG, "Error during backup!", e);
        BackupFileIOError.postNotificationForException(context, e, getRunAttempt());
        throw e;
      } finally {
        DocumentFile fileToCleanUp = backupDirectory.findFile(temporaryName);
        if (fileToCleanUp != null) {
          if (fileToCleanUp.delete()) {
            Log.w(TAG, "Backup failed. Deleted temp file");
          } else {
            Log.w(TAG, "Backup failed. Failed to delete temp file " + temporaryName);
          }
        }
      }

      BackupUtil.deleteOldBackups();
    } finally {
      EventBus.getDefault().unregister(updater);
      updater.setNotification(null);
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

  private static class ProgressUpdater {
    private NotificationController notification;

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onEvent(FullBackupBase.BackupEvent event) {
      if (notification == null) {
        return;
      }

      if (event.getType() == FullBackupBase.BackupEvent.Type.PROGRESS) {
        if (event.getEstimatedTotalCount() == 0) {
          notification.setIndeterminateProgress();
        } else {
          notification.setProgress(100, (int) event.getCompletionPercentage());
        }
      }
    }

    public void setNotification(NotificationController notification) {
      this.notification = notification;
    }
  }

  public static class Factory implements Job.Factory<LocalBackupJobApi29> {
    @Override
    public @NonNull
    LocalBackupJobApi29 create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LocalBackupJobApi29(parameters);
    }
  }
}
