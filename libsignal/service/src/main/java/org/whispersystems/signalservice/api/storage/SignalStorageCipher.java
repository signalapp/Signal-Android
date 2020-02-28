package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts data from the storage service.
 */
public class SignalStorageCipher {

  private static final int IV_LENGTH = 12;

  public static byte[] encrypt(StorageCipherKey key, byte[] data) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] iv     = Util.getSecretBytes(IV_LENGTH);

      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.serialize(), "AES"), new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(data);

      return Util.join(iv, ciphertext);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException  e) {
      throw new AssertionError(e);
    }
  }

  public static byte[] decrypt(StorageCipherKey key, byte[] data) throws InvalidKeyException {
    try {
      Cipher   cipher     = Cipher.getInstance("AES/GCM/NoPadding");
      byte[][] split      = Util.split(data, IV_LENGTH, data.length - IV_LENGTH);
      byte[]   iv         = split[0];
      byte[]   cipherText = split[1];

      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.serialize(), "AES"), new GCMParameterSpec(128, iv));
      return cipher.doFinal(cipherText);
    } catch (java.security.InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
      throw new InvalidKeyException(e);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }
}
