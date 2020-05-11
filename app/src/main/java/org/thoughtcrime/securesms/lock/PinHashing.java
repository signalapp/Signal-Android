package org.thoughtcrime.securesms.lock;

import androidx.annotation.NonNull;

import org.signal.argon2.Argon2;
import org.signal.argon2.Argon2Exception;
import org.signal.argon2.MemoryCost;
import org.signal.argon2.Type;
import org.signal.argon2.UnknownTypeException;
import org.signal.argon2.Version;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.internal.registrationpin.PinHasher;

public final class PinHashing {

  private PinHashing() {
  }

  public static HashedPin hashPin(@NonNull String pin, @NonNull KeyBackupService.HashSession hashSession) {
    return PinHasher.hashPin(PinHasher.normalize(pin), password -> {
      try {
        return new Argon2.Builder(Version.V13)
                         .type(Type.Argon2id)
                         .memoryCost(MemoryCost.MiB(16))
                         .parallelism(1)
                         .iterations(32)
                         .hashLength(64)
                         .build()
                         .hash(password, hashSession.hashSalt())
                         .getHash();
      } catch (Argon2Exception e) {
        throw new AssertionError(e);
      }
    });
  }

  public static String localPinHash(@NonNull String pin) {
    byte[] normalized = PinHasher.normalize(pin);
    try {
      return new Argon2.Builder(Version.V13)
                       .type(Type.Argon2i)
                       .memoryCost(MemoryCost.KiB(256))
                       .parallelism(1)
                       .iterations(50)
                       .hashLength(32)
                       .build()
                       .hash(normalized, Util.getSecretBytes(16))
                       .getEncoded();
    } catch (Argon2Exception e) {
      throw new AssertionError(e);
    }
  }

  public static boolean verifyLocalPinHash(@NonNull String localPinHash, @NonNull String pin) {
    byte[] normalized = PinHasher.normalize(pin);
    try {
      return Argon2.verify(localPinHash, normalized);
    } catch (UnknownTypeException e) {
      throw new AssertionError(e);
    }
  }
}
