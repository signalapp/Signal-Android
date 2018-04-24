package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;

import com.annimon.stream.Objects;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class StorageUtil
{

  public static File getBackupDirectory(Context context) throws NoExternalStorageException {
    File storage = null;

    if (Build.VERSION.SDK_INT >= 19) {
      File[] directories = context.getExternalFilesDirs(null);

      if (directories != null) {
        storage = getNonEmulated(directories);
      }
    }

    if (storage == null) {
      storage = Environment.getExternalStorageDirectory();
    }

    if (!storage.canWrite()) {
      throw new NoExternalStorageException();
    }

    File signal = new File(storage, "Signal");
    File backups = new File(signal, "Backups");

    if (!backups.exists()) {
      if (!backups.mkdirs()) {
        throw new NoExternalStorageException("Unable to create backup directory...");
      }
    }


    return backups;
  }

  public static File getBackupCacheDirectory(Context context) {
    if (Build.VERSION.SDK_INT >= 19) {
      File[] directories = context.getExternalCacheDirs();

      if (directories != null) {
        File result = getNonEmulated(directories);
        if (result != null) return result;
      }
    }

    return context.getExternalCacheDir();
  }

  private static @Nullable File getNonEmulated(File[] directories) {
    return Stream.of(directories)
                 .withoutNulls()
                 .filterNot(f -> f.getAbsolutePath().contains("emulated"))
                 .limit(1)
                 .findSingle()
                 .orElse(null);
  }

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

  public static File getLegacyBackupDirectory() throws NoExternalStorageException {
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

  public static @Nullable String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}
