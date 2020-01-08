package org.thoughtcrime.securesms.backup;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.whispersystems.libsignal.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class FullBackupBase {

  @SuppressWarnings("unused")
  private static final String TAG = FullBackupBase.class.getSimpleName();

  static class BackupStream {
    static @NonNull byte[] getBackupKey(@NonNull String passphrase, @Nullable byte[] salt) {
      try {
        EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, 0));

        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[]        input  = passphrase.replace(" ", "").getBytes();
        byte[]        hash   = input;

        if (salt != null) digest.update(salt);

        for (int i=0;i<250000;i++) {
          if (i % 1000 == 0) EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, 0));
          digest.update(hash);
          hash = digest.digest(input);
        }

        return ByteUtil.trim(hash, 32);
      } catch (NoSuchAlgorithmException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static class BackupEvent {
    public enum Type {
      PROGRESS,
      FINISHED
    }

    private final Type type;
    private final int count;

    BackupEvent(Type type, int count) {
      this.type  = type;
      this.count = count;
    }

    public Type getType() {
      return type;
    }

    public int getCount() {
      return count;
    }
  }

}
