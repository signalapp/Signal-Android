package org.thoughtcrime.securesms.util;

import android.os.Environment;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil
{
  private static File getSignalStorageDir() throws NoExternalStorageException {
    final File storage = Environment.getExternalStorageDirectory();

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    return storage;
  }

  public static boolean canWriteInSignalStorageDir() {
    File storage;

    try {
      storage = getSignalStorageDir();
    } catch (NoExternalStorageException e) {
      return false;
    }

    return storage.canWrite();
  }

  public static File getBackupDir() throws NoExternalStorageException {
    return getSignalStorageDir();
  }

  public static File getVideoDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_MOVIES);
  }

  public static File getAudioDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_MUSIC);
  }

  public static File getImageDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_PICTURES);
  }

  public static File getDownloadDir() throws NoExternalStorageException {
    return new File(getSignalStorageDir(), Environment.DIRECTORY_DOWNLOADS);
  }
}
