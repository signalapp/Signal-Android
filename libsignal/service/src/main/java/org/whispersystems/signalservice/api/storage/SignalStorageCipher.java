package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.CryptoUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts data from the storage service.
 */
public class SignalStorageCipher {

  private final byte[] key;

  public SignalStorageCipher(byte[] storageServiceKey) {
    this.key = storageServiceKey;
  }

  public byte[] encrypt(byte[] data) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] iv     = Util.getSecretBytes(16);

      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(data);

      return ByteUtil.combine(iv, ciphertext);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException  e) {
      throw new AssertionError(e);
    }
  }

  public byte[] decrypt(byte[] data) throws InvalidKeyException {
    try {
      Cipher   cipher     = Cipher.getInstance("AES/GCM/NoPadding");
      byte[][] split      = Util.split(data, 16, data.length - 16);
      byte[]   iv         = split[0];
      byte[]   cipherText = split[1];

      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      return cipher.doFinal(cipherText);
    } catch (java.security.InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
      throw new InvalidKeyException(e);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }
}
