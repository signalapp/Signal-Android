package org.whispersystems.signalservice.internal.registrationpin;

import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class PinStretcher {

  private static final String  HMAC_SHA256 = "HmacSHA256";
  private static final Charset UTF_8       = Charset.forName("UTF-8");

  public static StretchedPin stretchPin(CharSequence pin) throws InvalidPinException {
    return new StretchedPin(pin);
  }

  public static class StretchedPin {
    private final byte[] stretchedPin;
    private final byte[] pinKey1;
    private final byte[] kbsAccessKey;

    private StretchedPin(byte[] stretchedPin, byte[] pinKey1, byte[] kbsAccessKey) {
      this.stretchedPin = stretchedPin;
      this.pinKey1      = pinKey1;
      this.kbsAccessKey = kbsAccessKey;
    }

    private StretchedPin(CharSequence pin) throws InvalidPinException {
      if (pin.length() < 4) throw new InvalidPinException("Pin too short");

      char[] arabicPin = toArabic(pin);

      stretchedPin = pbkdf2HmacSHA256(arabicPin, "nosalt", 20000, 256);

      try {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(stretchedPin, HMAC_SHA256));
        mac.update("Master Key Encryption".getBytes(UTF_8));

        pinKey1 = new byte[32];
        mac.doFinal(pinKey1, 0);

        mac.init(new SecretKeySpec(stretchedPin, HMAC_SHA256));
        mac.update("KBS Access Key".getBytes(UTF_8));

        kbsAccessKey = new byte[32];
        mac.doFinal(kbsAccessKey, 0);
      } catch (NoSuchAlgorithmException | ShortBufferException | InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }

    public MasterKey withPinKey2(byte[] pinKey2) {
      return new MasterKey(pinKey1, pinKey2, this);
    }

    public MasterKey withNewSecurePinKey2() {
      return withPinKey2(Util.getSecretBytes(32));
    }

    public byte[] getPinKey1() {
      return pinKey1;
    }

    public byte[] getStretchedPin() {
      return stretchedPin;
    }

    public byte[] getKbsAccessKey() {
      return kbsAccessKey;
    }
  }

  public static class MasterKey extends StretchedPin {
    private final byte[] pinKey2;
    private final byte[] masterKey;
    private final String registrationLock;

    private MasterKey(byte[] pinKey1, byte[] pinKey2, StretchedPin stretchedPin) {
      super(stretchedPin.stretchedPin, stretchedPin.pinKey1, stretchedPin.kbsAccessKey);

      if (pinKey2.length != 32) {
        throw new AssertionError("PinKey2 must be exactly 32 bytes");
      }

      this.pinKey2 = pinKey2.clone();

      try {
        Mac mac = Mac.getInstance(HMAC_SHA256);

        mac.init(new SecretKeySpec(pinKey1, HMAC_SHA256));
        mac.update(pinKey2);

        masterKey = new byte[32];
        mac.doFinal(masterKey, 0);

        mac.init(new SecretKeySpec(masterKey, HMAC_SHA256));
        mac.update("Registration Lock".getBytes(UTF_8));

        byte[] registration_lock_token_bytes = new byte[32];
        mac.doFinal(registration_lock_token_bytes, 0);
        registrationLock = Hex.toStringCondensed(registration_lock_token_bytes);

      } catch (NoSuchAlgorithmException | ShortBufferException | InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }

    public byte[] getPinKey2() {
      return pinKey2;
    }

    public String getRegistrationLock() {
      return registrationLock;
    }

    public byte[] getMasterKey() {
      return masterKey;
    }
  }

  private static byte[] pbkdf2HmacSHA256(char[] pin, String salt, int iterationCount, int outputSize) {
    byte[] saltBytes = salt.getBytes(Charset.forName("UTF-8"));

    try {
      SecretKeyFactory skf     = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      PBEKeySpec       spec    = new PBEKeySpec(pin, saltBytes, iterationCount, outputSize);
      byte[]           encoded = skf.generateSecret(spec).getEncoded();

      spec.clearPassword();

      return encoded;
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new AssertionError("Could not stretch pin", e);
    }
  }

  /**
   * Converts a string of not necessarily Arabic numerals to Arabic 0..9 characters.
   */
  private static char[] toArabic(CharSequence numerals) throws InvalidPinException {
    int    length = numerals.length();
    char[] arabic = new char[length];

    for (int i = 0; i < length; i++) {
      int digit = Character.digit(numerals.charAt(i), 10);

      if (digit < 0) {
        throw new InvalidPinException("Pin must only consist of decimals");
      }

      arabic[i] = (char) ('0' + digit);
    }

    return arabic;
  }
}
