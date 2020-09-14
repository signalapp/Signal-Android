package org.thoughtcrime.securesms.util;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.BackupFileRecord;
import org.thoughtcrime.securesms.logging.Log;

import network.loki.messenger.R;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.File;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

//TODO AC: Delete this class when its functionality is
// fully replaced by the BackupUtil.kt and related classes.
/** @deprecated in favor of {@link BackupUtil} */
public class BackupUtilOld {

  private static final String TAG = BackupUtilOld.class.getSimpleName();

  /**
   * @deprecated this method exists only for the backward compatibility with the legacy Signal backup code.
   * Use {@link BackupUtil} if possible.
   */
  public static @Nullable BackupInfo getLatestBackup(Context context) throws NoExternalStorageException {
    BackupFileRecord backup = BackupUtil.getLastBackup(context);
    if (backup == null) return null;


    return new BackupInfo(
            backup.getTimestamp().getTime(),
            backup.getFileSize(),
            new File(backup.getUri().getPath()));
  }

  @Deprecated
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
