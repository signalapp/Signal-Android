package org.whispersystems.signalservice.internal.registrationpin;

import org.whispersystems.signalservice.api.kbs.HashedPin;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class PinHasher {

  public static byte[] normalize(String pin) {
    pin = pin.trim();

    if (PinString.allNumeric(pin)) {
      pin = PinString.toArabic(pin);
    }

    pin = Normalizer.normalize(pin, Normalizer.Form.NFKD);

    return pin.getBytes(StandardCharsets.UTF_8);
  }

  public static HashedPin hashPin(byte[] normalizedPinBytes, Argon2 argon2) {
    return HashedPin.fromArgon2Hash(argon2.hash(normalizedPinBytes));
  }

  public interface Argon2 {
    byte[] hash(byte[] password);
  }
}
