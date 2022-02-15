package org.thoughtcrime.securesms.backup;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.whispersystems.libsignal.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class FullBackupBase {

  private static final int DIGEST_ROUNDS = 250_000;

  static class BackupStream {
    static @NonNull byte[] getBackupKey(@NonNull String passphrase, @Nullable byte[] salt) {
      try {
        EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, 0, 0));

        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[]        input  = passphrase.replace(" ", "").getBytes();
        byte[]        hash   = input;

        if (salt != null) digest.update(salt);

        for (int i = 0; i < DIGEST_ROUNDS; i++) {
          if (i % 1000 == 0) EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, 0, 0));
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
    private final long count;
    private final long estimatedTotalCount;

    BackupEvent(Type type, long count, long estimatedTotalCount) {
      this.type                = type;
      this.count               = count;
      this.estimatedTotalCount = estimatedTotalCount;
    }

    public Type getType() {
      return type;
    }

    public long getCount() {
      return count;
    }

    public long getEstimatedTotalCount() {
      return estimatedTotalCount;
    }

    public double getCompletionPercentage() {
      if (estimatedTotalCount == 0) {
        return 0;
      }

      return Math.min(99.9f, (double) count * 100L / (double) estimatedTotalCount);
    }
  }

}
