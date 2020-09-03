package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Environment;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil {

  public static File getBackupDirectory(Context context) throws NoExternalStorageException {
    File storage = context.getExternalFilesDir(null);

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    File session = new File(storage, "Session");
    File backups = new File(session, "Backups");

    if (!backups.exists()) {
      if (!backups.mkdirs()) {
        throw new NoExternalStorageException("Unable to create backup directory...");
      }
    }

    return backups;
  }

  public static File getBackupCacheDirectory(Context context) {
    return context.getExternalCacheDir();
  }

  private static File getSessionStorageDir(Context context) throws NoExternalStorageException {
    final File storage = context.getExternalFilesDir(null);

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    return storage;
  }

  public static boolean canWriteInSessionStorageDir(Context context) {
    File storage;

    try {
      storage = getSessionStorageDir(context);
    } catch (NoExternalStorageException e) {
      return false;
    }

    return storage.canWrite();
  }

  public static File getLegacyBackupDirectory(Context context) throws NoExternalStorageException {
    return getSessionStorageDir(context);
  }

  public static File getVideoDir(Context context) throws NoExternalStorageException {
    return new File(getSessionStorageDir(context), Environment.DIRECTORY_MOVIES);
  }

  public static File getAudioDir(Context context) throws NoExternalStorageException {
    return new File(getSessionStorageDir(context), Environment.DIRECTORY_MUSIC);
  }

  public static File getImageDir(Context context) throws NoExternalStorageException {
    return new File(getSessionStorageDir(context), Environment.DIRECTORY_PICTURES);
  }

  public static File getDownloadDir(Context context) throws NoExternalStorageException {
    return new File(getSessionStorageDir(context), Environment.DIRECTORY_DOWNLOADS);
  }

  public static @Nullable String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}
