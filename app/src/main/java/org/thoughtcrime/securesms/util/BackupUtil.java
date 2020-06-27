package org.thoughtcrime.securesms.util;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BackupUtil {

  private static final String TAG = BackupUtil.class.getSimpleName();

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

  private static List<BackupInfo> getAllBackupsNewestFirst() throws NoExternalStorageException {
    File             backupDirectory = StorageUtil.getBackupDirectory();
    File[]           files           = backupDirectory.listFiles();
    List<BackupInfo> backups         = new ArrayList<>(files.length);

    for (File file : files) {
      if (file.isFile() && file.getAbsolutePath().endsWith(".backup")) {
        long backupTimestamp = getBackupTimestamp(file);

        if (backupTimestamp != -1) {
          backups.add(new BackupInfo(backupTimestamp, file.length(), file));
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
      result[i/5] = String.format("%05d", ByteUtil.byteArray5ToLong(random, i) % 100000);
    }

    return result;
  }

  private static long getBackupTimestamp(File backup) {
    String   name  = backup.getName();
    String[] prefixSuffix = name.split("[.]");

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

  public static class BackupInfo {

    private final long timestamp;
    private final long size;
    private final File file;

    BackupInfo(long timestamp, long size, File file) {
      this.timestamp = timestamp;
      this.size      = size;
      this.file      = file;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getSize() {
      return size;
    }

    public File getFile() {
      return file;
    }

    private void delete() {
      Log.i(TAG, "Deleting: " + file.getAbsolutePath());

      if (!file.delete()) {
        Log.w(TAG, "Delete failed: " + file.getAbsolutePath());
      }
    }
  }
}
