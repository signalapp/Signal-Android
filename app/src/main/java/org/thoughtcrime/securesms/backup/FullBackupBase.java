package org.thoughtcrime.securesms.backup;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.libsignal.protocol.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class FullBackupBase {

  private static final int DIGEST_ROUNDS = 250_000;

  static class BackupStream {
    static @NonNull byte[] getBackupKey(@NonNull String passphrase, @Nullable byte[] salt) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[]        input  = passphrase.replace(" ", "").getBytes();
        byte[]        hash   = input;

        if (salt != null) digest.update(salt);

        for (int i = 0; i < DIGEST_ROUNDS; i++) {
          digest.update(hash);
          hash = digest.digest(input);
        }

        return ByteUtil.trim(hash, 32);
      } catch (NoSuchAlgorithmException e) {
        throw new AssertionError(e);
      }
    }
  }
}
