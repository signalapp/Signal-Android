package org.thoughtcrime.securesms.util;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.documentfile.provider.DocumentFile;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BackupUtil {

  private static final String TAG = Log.tag(BackupUtil.class);

  public static final int PASSPHRASE_LENGTH = 30;

  public static @NonNull String getLastBackupTime(@NonNull Context context, @NonNull Locale locale) {
    try {
      BackupInfo backup = getLatestBackup();

      if (backup == null) return context.getString(R.string.BackupUtil_never);
      else                return DateUtils.getExtendedRelativeTimeSpanString(context, locale, backup.getTimestamp());
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
      return context.getString(R.string.BackupUtil_unknown);
    }
  }

  public static boolean isUserSelectionRequired(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 29 && !Permissions.hasAll(context, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
  }

  public static boolean canUserAccessBackupDirectory(@NonNull Context context) {
    if (isUserSelectionRequired(context)) {
      Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
      if (backupDirectoryUri == null) {
        return false;
      }

      DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
      return backupDirectory != null && backupDirectory.exists() && backupDirectory.canRead() && backupDirectory.canWrite();
    } else {
      return Permissions.hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
  }

  public static @Nullable BackupInfo getLatestBackup() throws NoExternalStorageException {
    List<BackupInfo> backups = getAllBackupsNewestFirst();

    return backups.isEmpty() ? null : backups.get(0);
  }

  public static void deleteAllBackups() {
    Log.i(TAG, "Deleting all backups");

    try {
      List<BackupInfo> backups = getAllBackupsNewestFirst();

      for (BackupInfo backup : backups) {
        backup.delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void deleteOldBackups() {
    Log.i(TAG, "Deleting older backups");

    try {
      List<BackupInfo> backups = getAllBackupsNewestFirst();

      for (int i = 2; i < backups.size(); i++) {
        backups.get(i).delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void disableBackups(@NonNull Context context) {
    BackupPassphrase.set(context, null);
    SignalStore.settings().setBackupEnabled(false);
    BackupUtil.deleteAllBackups();

    if (BackupUtil.isUserSelectionRequired(context)) {
      Uri backupLocationUri = SignalStore.settings().getSignalBackupDirectory();

      if (backupLocationUri == null) {
        return;
      }

      SignalStore.settings().clearSignalBackupDirectory();

      try {
        context.getContentResolver()
            .releasePersistableUriPermission(Objects.requireNonNull(backupLocationUri),
                                             Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                             Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      } catch (SecurityException e) {
        Log.w(TAG, "Could not release permissions", e);
      }
    }
  }

  private static List<BackupInfo> getAllBackupsNewestFirst() throws NoExternalStorageException {
    if (isUserSelectionRequired(AppDependencies.getApplication())) {
      return getAllBackupsNewestFirstApi29();
    } else {
      return getAllBackupsNewestFirstLegacy();
    }
  }

  @RequiresApi(29)
  private static List<BackupInfo> getAllBackupsNewestFirstApi29() {
    Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
    if (backupDirectoryUri == null) {
      Log.i(TAG, "Backup directory is not set. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile backupDirectory = DocumentFile.fromTreeUri(AppDependencies.getApplication(), backupDirectoryUri);
    if (backupDirectory == null || !backupDirectory.exists() || !backupDirectory.canRead()) {
      Log.w(TAG, "Backup directory is inaccessible. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile[]   files   = backupDirectory.listFiles();
    List<BackupInfo> backups = new ArrayList<>(files.length);

    for (DocumentFile file : files) {
      if (file.isFile() && file.getName() != null && file.getName().endsWith(".backup")) {
        long backupTimestamp = getBackupTimestamp(file.getName());

        if (backupTimestamp != -1) {
          backups.add(new BackupInfo(backupTimestamp, file.length(), file.getUri()));
        }
      }
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  public static @Nullable BackupInfo getBackupInfoFromSingleUri(@NonNull Context context, @NonNull Uri singleUri) throws BackupFileException {
    DocumentFile documentFile = Objects.requireNonNull(DocumentFile.fromSingleUri(context, singleUri));

    return getBackupInfoFromSingleDocumentFile(documentFile);
  }

  @VisibleForTesting
  static @Nullable BackupInfo getBackupInfoFromSingleDocumentFile(@NonNull DocumentFile documentFile) throws BackupFileException {
    BackupFileState backupFileState = getBackupFileState(documentFile);

    if (backupFileState.isSuccess()) {
      long backupTimestamp = getBackupTimestamp(Objects.requireNonNull(documentFile.getName()));
      return new BackupInfo(backupTimestamp, documentFile.length(), documentFile.getUri());
    } else {
      Log.w(TAG, "Could not load backup info.");
      backupFileState.throwIfError();
      return null;
    }
  }

  private static List<BackupInfo> getAllBackupsNewestFirstLegacy() throws NoExternalStorageException {
    File             backupDirectory = StorageUtil.getOrCreateBackupDirectory();
    File[]           files           = backupDirectory.listFiles();
    List<BackupInfo> backups         = new ArrayList<>(files.length);

    for (File file : files) {
      if (file.isFile() && file.getAbsolutePath().endsWith(".backup")) {
        long backupTimestamp = getBackupTimestamp(file.getName());

        if (backupTimestamp != -1) {
          backups.add(new BackupInfo(backupTimestamp, file.length(), Uri.fromFile(file)));
        }
      }
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  public static @NonNull String[] generateBackupPassphrase() {
    String[] result = new String[6];
    byte[]   random = new byte[30];

    new SecureRandom().nextBytes(random);

    for (int i=0;i<30;i+=5) {
      result[i/5] = String.format(Locale.ENGLISH,  "%05d", ByteUtil.byteArray5ToLong(random, i) % 100000);
    }

    return result;
  }

  public static boolean hasBackupFiles(@NonNull Context context) {
    if (Permissions.hasAll(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
      try {
        File directory = StorageUtil.getBackupDirectory();

        if (directory.exists() && directory.isDirectory()) {
          File[] files = directory.listFiles();
          return files != null && files.length > 0;
        } else {
          return false;
        }
      } catch (NoExternalStorageException e) {
        Log.w(TAG, "Failed to read storage!", e);
        return false;
      }
    } else {
      return false;
    }
  }

  private static long getBackupTimestamp(@NonNull String backupName) {
    String[] prefixSuffix = backupName.split("[.]");

    if (prefixSuffix.length == 2) {
      String[] parts = prefixSuffix[0].split("\\-");

      if (parts.length == 7) {
        try {
          Calendar calendar = Calendar.getInstance();
          calendar.set(Calendar.YEAR, Integer.parseInt(parts[1]));
          calendar.set(Calendar.MONTH, Integer.parseInt(parts[2]) - 1);
          calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[3]));
          calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[4]));
          calendar.set(Calendar.MINUTE, Integer.parseInt(parts[5]));
          calendar.set(Calendar.SECOND, Integer.parseInt(parts[6]));
          calendar.set(Calendar.MILLISECOND, 0);

          return calendar.getTimeInMillis();
        } catch (NumberFormatException e) {
          Log.w(TAG, e);
        }
      }
    }

    return -1;
  }

  private static BackupFileState getBackupFileState(@NonNull DocumentFile documentFile) {
    if (!documentFile.exists()) {
      return BackupFileState.NOT_FOUND;
    } else if (!documentFile.canRead()) {
      return BackupFileState.NOT_READABLE;
    } else if (Util.isEmpty(documentFile.getName()) || !documentFile.getName().endsWith(".backup")) {
      return BackupFileState.UNSUPPORTED_FILE_EXTENSION;
    } else {
      return BackupFileState.READABLE;
    }
  }

  /**
   * Describes the validity of a backup file.
   */
  public enum BackupFileState {
    READABLE("The document at the specified Uri looks like a readable backup."),
    NOT_FOUND("The document at the specified Uri cannot be found."),
    NOT_READABLE("The document at the specified Uri cannot be read."),
    UNSUPPORTED_FILE_EXTENSION("The document at the specified Uri has an unsupported file extension.");

    private final String message;

    BackupFileState(String message) {
      this.message = message;
    }

    public boolean isSuccess() {
      return this == READABLE;
    }

    public void throwIfError() throws BackupFileException {
      if (!isSuccess()) {
        throw new BackupFileException(this);
      }
    }
  }

  /**
   * Wrapping exception for a non-successful BackupFileState.
   */
  public static class BackupFileException extends Exception {

    private final BackupFileState state;

    BackupFileException(BackupFileState backupFileState) {
      super(backupFileState.message);
      this.state = backupFileState;
    }

    public @NonNull BackupFileState getState() {
      return state;
    }
  }

  public static class BackupInfo {

    private final long timestamp;
    private final long size;
    private final Uri  uri;

    BackupInfo(long timestamp, long size, Uri uri) {
      this.timestamp = timestamp;
      this.size      = size;
      this.uri       = uri;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getSize() {
      return size;
    }

    public Uri getUri() {
      return uri;
    }

    private void delete() {
      File file = new File(Objects.requireNonNull(uri.getPath()));

      if (file.exists()) {
        Log.i(TAG, "Deleting File: " + file.getAbsolutePath());

        if (!file.delete()) {
          Log.w(TAG, "Delete failed: " + file.getAbsolutePath());
        }
      } else {
        DocumentFile document = DocumentFile.fromSingleUri(AppDependencies.getApplication(), uri);
        if (document != null && document.exists()) {
          Log.i(TAG, "Deleting DocumentFile: " + uri);

          if (!document.delete()) {
            Log.w(TAG, "Delete failed: " + uri);
          }
        }
      }
    }
  }
}
