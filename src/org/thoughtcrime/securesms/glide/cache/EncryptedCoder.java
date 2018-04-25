package org.thoughtcrime.securesms.glide.cache;


import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class EncryptedCoder {

  private static byte[] MAGIC_BYTES = {(byte)0x91, (byte)0x5e, (byte)0x6d, (byte)0xb4,
                                       (byte)0x09, (byte)0xa6, (byte)0x68, (byte)0xbe,
                                       (byte)0xe5, (byte)0xb1, (byte)0x1b, (byte)0xd7,
                                       (byte)0x29, (byte)0xe5, (byte)0x04, (byte)0xcc};

  OutputStream createEncryptedOutputStream(@NonNull byte[] masterKey, @NonNull File file)
      throws IOException
  {
    try {
      byte[] random = Util.getSecretBytes(32);
      Mac    mac    = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));

      FileOutputStream fileOutputStream = new FileOutputStream(file);
      byte[]           iv               = new byte[16];
      byte[]           key              = mac.doFinal(random);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      fileOutputStream.write(MAGIC_BYTES);
      fileOutputStream.write(random);

      CipherOutputStream outputStream = new CipherOutputStream(fileOutputStream, cipher);
      outputStream.write(MAGIC_BYTES);

      return outputStream;
    } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  InputStream createEncryptedInputStream(@NonNull byte[] masterKey, @NonNull File file) throws IOException {
    try {
      Mac    mac    = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));

      FileInputStream fileInputStream     = new FileInputStream(file);
      byte[]          theirMagic          = new byte[MAGIC_BYTES.length];
      byte[]          theirRandom         = new byte[32];
      byte[]          theirEncryptedMagic = new byte[MAGIC_BYTES.length];

      Util.readFully(fileInputStream, theirMagic);
      Util.readFully(fileInputStream, theirRandom);

      if (!MessageDigest.isEqual(theirMagic, MAGIC_BYTES)) {
        throw new IOException("Not an encrypted cache file!");
      }

      byte[] iv  = new byte[16];
      byte[] key = mac.doFinal(theirRandom);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      CipherInputStream inputStream = new CipherInputStream(fileInputStream, cipher);
      Util.readFully(inputStream, theirEncryptedMagic);

      if (!MessageDigest.isEqual(theirEncryptedMagic, MAGIC_BYTES)) {
        throw new IOException("Key change on encrypted cache file!");
      }

      return inputStream;
    } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }


}
