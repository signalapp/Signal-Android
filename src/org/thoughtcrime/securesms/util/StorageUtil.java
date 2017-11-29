package org.thoughtcrime.securesms.util;

import android.os.Environment;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil
{
  private static final String BACKUP_SUBDIRECTORY = "Signal";

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
    return new File(getSignalStorageDir(), BACKUP_SUBDIRECTORY);
  }

  public static File getVideoDir() throws NoExternalStorageException {
    File rootVideoDir = new File(getSignalStorageDir(), Environment.DIRECTORY_MOVIES);
    return new File(rootVideoDir, BACKUP_SUBDIRECTORY);
  }

  public static File getAudioDir() throws NoExternalStorageException {
    File rootAudioDir = new File(getSignalStorageDir(), Environment.DIRECTORY_MUSIC);
    return new File(rootAudioDir, BACKUP_SUBDIRECTORY);
  }

  public static File getImageDir() throws NoExternalStorageException {
    File rootImageDir = new File(getSignalStorageDir(), Environment.DIRECTORY_PICTURES);
    return new File(rootImageDir, BACKUP_SUBDIRECTORY);
  }

  public static File getDownloadDir() throws NoExternalStorageException {
    File rootDownloadDir = new File(getSignalStorageDir(), Environment.DIRECTORY_DOWNLOADS);
    return new File(rootDownloadDir, BACKUP_SUBDIRECTORY);
  }
}
