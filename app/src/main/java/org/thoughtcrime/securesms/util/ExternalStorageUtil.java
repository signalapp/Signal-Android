package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.File;

public class ExternalStorageUtil {

  public static final String DIRECTORY_BACKUPS = "Backups";

  /** @see Context#getExternalFilesDir(String) */
  @NonNull
  public static File getDir(Context context, @Nullable String type) throws NoExternalStorageException {
    final File dir = context.getExternalFilesDir(type);
    if (dir == null) {
      throw new NoExternalStorageException("External storage dir is currently unavailable: " + type);
    }
    return dir;
  }

  @NonNull
  public static File getBackupDir(Context context) throws NoExternalStorageException {
    return getDir(context, DIRECTORY_BACKUPS);
  }

  @NonNull
  public static File getVideoDir(Context context) throws NoExternalStorageException {
    return getDir(context, Environment.DIRECTORY_MOVIES);
  }

  @NonNull
  public static File getAudioDir(Context context) throws NoExternalStorageException {
    return getDir(context, Environment.DIRECTORY_MUSIC);
  }

  @NonNull
  public static File getImageDir(Context context) throws NoExternalStorageException {
    return getDir(context, Environment.DIRECTORY_PICTURES);
  }

  @NonNull
  public static File getDownloadDir(Context context) throws NoExternalStorageException {
    return getDir(context, Environment.DIRECTORY_DOWNLOADS);
  }

  @Nullable
  public static File getCacheDir(Context context) {
    return context.getExternalCacheDir();
  }

  @Nullable
  public static String getCleanFileName(@Nullable String fileName) {
    if (fileName == null) return null;

    fileName = fileName.replace('\u202D', '\uFFFD');
    fileName = fileName.replace('\u202E', '\uFFFD');

    return fileName;
  }
}
