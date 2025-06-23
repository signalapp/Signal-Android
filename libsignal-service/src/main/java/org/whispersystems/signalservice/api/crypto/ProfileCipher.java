package org.whispersystems.signalservice.api.crypto;


import org.signal.libsignal.protocol.util.ByteUtil;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ProfileCipher {

  private static final int NAME_PADDED_LENGTH_1 = 53;
  private static final int NAME_PADDED_LENGTH_2 = 257;
  private static final int ABOUT_PADDED_LENGTH_1 = 128;
  private static final int ABOUT_PADDED_LENGTH_2 = 254;
  private static final int ABOUT_PADDED_LENGTH_3 = 512;

  public static final int MAX_POSSIBLE_NAME_LENGTH  = NAME_PADDED_LENGTH_2;
  public static final int MAX_POSSIBLE_ABOUT_LENGTH = ABOUT_PADDED_LENGTH_3;
  public static final int EMOJI_PADDED_LENGTH       = 32;
  public static final int ENCRYPTION_OVERHEAD       = 28;

  public static final int PAYMENTS_ADDRESS_BASE64_FIELD_SIZE = 776;
  public static final int PAYMENTS_ADDRESS_CONTENT_SIZE      = PAYMENTS_ADDRESS_BASE64_FIELD_SIZE * 6 / 8 - ProfileCipher.ENCRYPTION_OVERHEAD;

  private final ProfileKey key;

  public ProfileCipher(ProfileKey key) {
    this.key = key;
  }

  /**
   * Encrypts an input and ensures padded length.
   * <p>
   * Padded length does not include {@link #ENCRYPTION_OVERHEAD}.
   */
  public byte[] encrypt(byte[] input, int paddedLength) {
    try {
      byte[] inputPadded = new byte[paddedLength];

      if (input.length > inputPadded.length) {
        throw new IllegalArgumentException("Input is too long: " + new String(input));
      }

      System.arraycopy(input, 0, inputPadded, 0, input.length);

      byte[] nonce = Util.getSecretBytes(12);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.serialize(), "AES"), new GCMParameterSpec(128, nonce));

      byte[] encryptedPadded = ByteUtil.combine(nonce, cipher.doFinal(inputPadded));

      if (encryptedPadded.length != (paddedLength + ENCRYPTION_OVERHEAD)) {
        throw new AssertionError(String.format(Locale.US, "Wrong output length %d != padded length %d + %d", encryptedPadded.length, paddedLength, ENCRYPTION_OVERHEAD));
      }

      return encryptedPadded;
    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Returns original data with padding still intact.
   */
  public byte[] decrypt(byte[] input) throws InvalidCiphertextException {
    try {
      if (input.length < 12 + 16 + 1) {
        throw new InvalidCiphertextException("Too short: " + input.length);
      }

      byte[] nonce = new byte[12];
      System.arraycopy(input, 0, nonce, 0, nonce.length);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.serialize(), "AES"), new GCMParameterSpec(128, nonce));

      return cipher.doFinal(input, nonce.length, input.length - nonce.length);
    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException | BadPaddingException e) {
      throw new InvalidCiphertextException(e);
    }
  }

  /**
   * Encrypts a string's UTF bytes representation.
   */
  public byte[] encryptString(@Nonnull String input, int paddedLength) {
    return encrypt(input.getBytes(StandardCharsets.UTF_8), paddedLength);
  }

  /**
   * Strips 0 char padding from decrypt result.
   */
  public String decryptString(byte[] input) throws InvalidCiphertextException {
    byte[] paddedPlaintext = decrypt(input);
    int    plaintextLength = 0;

    for (int i = paddedPlaintext.length - 1; i >= 0; i--) {
      if (paddedPlaintext[i] != (byte) 0x00) {
        plaintextLength = i + 1;
        break;
      }
    }

    byte[] plaintext = new byte[plaintextLength];
    System.arraycopy(paddedPlaintext, 0, plaintext, 0, plaintextLength);

    return new String(plaintext);
  }

  public byte[] encryptBoolean(boolean input) {
    byte[] value = new byte[1];
    value[0] = (byte) (input ? 1 : 0);

    return encrypt(value, value.length);
  }

  public Optional<Boolean> decryptBoolean(@Nullable byte[] input) throws InvalidCiphertextException {
    if (input == null) {
      return Optional.empty();
    }

    byte[] paddedPlaintext = decrypt(input);
    return Optional.of(paddedPlaintext[0] != 0);
  }

  /**
   * Encodes the length, and adds padding.
   * <p>
   * encrypt(input.length | input | padding)
   * <p>
   * Padded length does not include 28 bytes encryption overhead.
   */
  public byte[] encryptWithLength(byte[] input, int paddedLength) {
    ByteBuffer content = ByteBuffer.wrap(new byte[input.length + 4]);
    content.order(ByteOrder.LITTLE_ENDIAN);
    content.putInt(input.length);
    content.put(input);
    return encrypt(content.array(), paddedLength);
  }

  /**
   * Extracts result from:
   * <p>
   * decrypt(encrypt(result.length | result | padding))
   */
  public byte[] decryptWithLength(byte[] input) throws InvalidCiphertextException, IOException {
    byte[]     decrypted = decrypt(input);
    int        maxLength = decrypted.length - 4;
    ByteBuffer content   = ByteBuffer.wrap(decrypted);
    content.order(ByteOrder.LITTLE_ENDIAN);
    int contentLength = content.getInt();
    if (contentLength > maxLength) {
      throw new IOException("Encoded length exceeds content length");
    }
    if (contentLength < 0) {
      throw new IOException("Encoded length is less than 0");
    }
    byte[] result = new byte[contentLength];
    content.get(result);
    return result;
  }

  public boolean verifyUnidentifiedAccess(byte[] theirUnidentifiedAccessVerifier) {
    try {
      if (theirUnidentifiedAccessVerifier == null || theirUnidentifiedAccessVerifier.length == 0) return false;

      byte[] unidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(key);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(unidentifiedAccessKey, "HmacSHA256"));

      byte[] ourUnidentifiedAccessVerifier = mac.doFinal(new byte[32]);

      return MessageDigest.isEqual(theirUnidentifiedAccessVerifier, ourUnidentifiedAccessVerifier);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static int getTargetNameLength(String name) {
    int nameLength = name.getBytes(StandardCharsets.UTF_8).length;

    if (nameLength <= NAME_PADDED_LENGTH_1) {
      return NAME_PADDED_LENGTH_1;
    } else {
      return NAME_PADDED_LENGTH_2;
    }
  }

  public static int getTargetAboutLength(String about) {
    int aboutLength = about.getBytes(StandardCharsets.UTF_8).length;

    if (aboutLength <= ABOUT_PADDED_LENGTH_1) {
      return ABOUT_PADDED_LENGTH_1;
    } else if (aboutLength < ABOUT_PADDED_LENGTH_2){
      return ABOUT_PADDED_LENGTH_2;
    } else {
      return ABOUT_PADDED_LENGTH_3;
    }
  }
}
