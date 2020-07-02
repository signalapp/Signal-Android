package org.whispersystems.signalservice.internal.contacts.crypto;

import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AESCipher {

  private static final int TAG_LENGTH_BYTES = 16;
  private static final int TAG_LENGTH_BITS  = TAG_LENGTH_BYTES * 8;

  static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] tag) throws InvalidCiphertextException {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

      return cipher.doFinal(ByteUtil.combine(ciphertext, tag));
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException | BadPaddingException e) {
      throw new InvalidCiphertextException(e);
    }
  }

  static AESEncryptedResult encrypt(byte[] key, byte[] aad, byte[] requestData) {
    try {
      byte[] iv     = Util.getSecretBytes(12);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      if (aad != null) {
        cipher.updateAAD(aad);
      }

      byte[]   cipherText = cipher.doFinal(requestData);
      byte[][] parts      = ByteUtil.split(cipherText, cipherText.length - TAG_LENGTH_BYTES, TAG_LENGTH_BYTES);

      byte[] mac  = parts[1];
      byte[] data = parts[0];

      return new AESEncryptedResult(iv, data, mac, aad);
    } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  static class AESEncryptedResult {
    final byte[] iv;
    final byte[] data;
    final byte[] mac;
    final byte[] aad;

    private AESEncryptedResult(byte[] iv, byte[] data, byte[] mac, byte[] aad) {
      this.iv   = iv;
      this.data = data;
      this.mac  = mac;
      this.aad  = aad;
    }
  }
}
