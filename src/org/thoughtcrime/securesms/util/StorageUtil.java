package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil {

  public static File getBackupDirectory() throws NoExternalStorageException {
    File storage = Environment.getExternalStorageDirectory();

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

  private static File getSessionStorageDir() throws NoExternalStorageException {
    final File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    return storage;
  }

  public static boolean canWriteInSessionStorageDir() {
    File storage;

    try {
      storage = getSessionStorageDir();
    } catch (NoExternalStorageException e) {
      return false;
    }

    return storage.canWrite();
  }

  public static File getLegacyBackupDirectory() throws NoExternalStorageException {
    return getSessionStorageDir();
  }

  public static File getVideoDir() throws NoExternalStorageException {
    return new File(getSessionStorageDir(), Environment.DIRECTORY_MOVIES);
  }

  public static File getAudioDir() throws NoExternalStorageException {
    return new File(getSessionStorageDir(), Environment.DIRECTORY_MUSIC);
  }

  public static File getImageDir() throws NoExternalStorageException {
    return new File(getSessionStorageDir(), Environment.DIRECTORY_PICTURES);
  }

  public static File getDownloadDir() throws NoExternalStorageException {
    return new File(getSessionStorageDir(), Environment.DIRECTORY_DOWNLOADS);
  }

  public static @Nullable String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}
