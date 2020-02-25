package org.whispersystems.signalservice.internal.registrationpin;

import org.whispersystems.signalservice.api.kbs.HashedPin;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class PinHasher {

  public static byte[] normalize(String pin) {
    pin = pin.trim();

    if (allNumeric(pin)) {
      pin = new String(toArabic(pin));
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

  private static boolean allNumeric(CharSequence pin) {
    for (int i = 0; i < pin.length(); i++) {
      if (!Character.isDigit(pin.charAt(i))) return false;
    }
    return true;
  }

  /**
   * Converts a string of not necessarily Arabic numerals to Arabic 0..9 characters.
   */
  private static char[] toArabic(CharSequence numerals) {
    int length = numerals.length();
    char[] arabic = new char[length];

    for (int i = 0; i < length; i++) {
      int digit = Character.digit(numerals.charAt(i), 10);

      arabic[i] = (char) ('0' + digit);
    }

    return arabic;
  }
}
