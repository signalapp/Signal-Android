package org.thoughtcrime.securesms.util;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.File;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class BackupUtil {

  private static final String TAG = BackupUtil.class.getSimpleName();

  public static @NonNull String getLastBackupTime(@NonNull Context context, @NonNull Locale locale) {
    try {
      BackupInfo backup = getLatestBackup(context);

      if (backup == null) return context.getString(R.string.BackupUtil_never);
      else                return DateUtils.getExtendedRelativeTimeSpanString(context, locale, backup.getTimestamp());
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
      return context.getString(R.string.BackupUtil_unknown);
    }
  }

  public static @Nullable BackupInfo getLatestBackup(@NonNull Context context) throws NoExternalStorageException {
    File       backupDirectory = StorageUtil.getBackupDirectory(context);
    File[]     backups         = backupDirectory.listFiles();
    BackupInfo latestBackup    = null;

    for (File backup : backups) {
      long backupTimestamp = getBackupTimestamp(backup);

      if (latestBackup == null || (backupTimestamp != -1 && backupTimestamp > latestBackup.getTimestamp())) {
        latestBackup = new BackupInfo(backupTimestamp, backup.length(), backup);
      }
    }

    return latestBackup;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void deleteAllBackups(@NonNull Context context) {
    try {
      File   backupDirectory = StorageUtil.getBackupDirectory(context);
      File[] backups         = backupDirectory.listFiles();

      for (File backup : backups) {
        if (backup.isFile()) backup.delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void deleteOldBackups(@NonNull Context context) {
    try {
      File   backupDirectory = StorageUtil.getBackupDirectory(context);
      File[] backups         = backupDirectory.listFiles();

      if (backups != null && backups.length > 2) {
        Arrays.sort(backups, (left, right) -> {
          long leftTimestamp  = getBackupTimestamp(left);
          long rightTimestamp = getBackupTimestamp(right);

          if      (leftTimestamp == -1 && rightTimestamp == -1) return 0;
          else if (leftTimestamp == -1)                         return 1;
          else if (rightTimestamp == -1)                        return -1;

          return (int)(rightTimestamp - leftTimestamp);
        });

        for (int i=2;i<backups.length;i++) {
          Log.w(TAG, "Deleting: " + backups[i].getAbsolutePath());

          if (!backups[i].delete()) {
            Log.w(TAG, "Delete failed: " + backups[i].getAbsolutePath());
          }
        }
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
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
  }
}
