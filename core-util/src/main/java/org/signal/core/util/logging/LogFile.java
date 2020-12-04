package org.signal.core.util.logging;

import androidx.annotation.NonNull;

import org.signal.core.util.Conversions;
import org.signal.core.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class LogFile {

  public static class Writer {

    private final byte[]        ivBuffer         = new byte[16];
    private final GrowingBuffer ciphertextBuffer = new GrowingBuffer();

    private final byte[]               secret;
    private final File                 file;
    private final Cipher               cipher;
    private final BufferedOutputStream outputStream;

    Writer(@NonNull byte[] secret, @NonNull File file) throws IOException {
      this.secret       = secret;
      this.file         = file;
      this.outputStream = new BufferedOutputStream(new FileOutputStream(file, true));

      try {
        this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
        throw new AssertionError(e);
      }
    }

    void writeEntry(@NonNull String entry) throws IOException {
      new SecureRandom().nextBytes(ivBuffer);

      byte[] plaintext = entry.getBytes();
      try {
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(ivBuffer));

        int    cipherLength = cipher.getOutputSize(plaintext.length);
        byte[] ciphertext   = ciphertextBuffer.get(cipherLength);
        cipherLength = cipher.doFinal(plaintext, 0, plaintext.length, ciphertext);

        outputStream.write(ivBuffer);
        outputStream.write(Conversions.intToByteArray(cipherLength));
        outputStream.write(ciphertext, 0, cipherLength);

        outputStream.flush();
      } catch (ShortBufferException | InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
        throw new AssertionError(e);
      }
    }

    long getLogSize() {
      return file.length();
    }

    void close() {
      StreamUtil.close(outputStream);
    }
  }

  static class Reader {

    private final byte[]        ivBuffer         = new byte[16];
    private final byte[]        intBuffer        = new byte[4];
    private final GrowingBuffer ciphertextBuffer = new GrowingBuffer();

    private final byte[]              secret;
    private final Cipher              cipher;
    private final BufferedInputStream inputStream;

    Reader(@NonNull byte[] secret, @NonNull File file) throws IOException {
      this.secret      = secret;
      this.inputStream = new BufferedInputStream(new FileInputStream(file));

      try {
        this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
        throw new AssertionError(e);
      }
    }

    String readAll() throws IOException {
      StringBuilder builder = new StringBuilder();

      String entry;
      while ((entry = readEntry()) != null) {
        builder.append(entry).append('\n');
      }

      return builder.toString();
    }

    private String readEntry() throws IOException {
      try {
        StreamUtil.readFully(inputStream, ivBuffer);
        StreamUtil.readFully(inputStream, intBuffer);

        int    length     = Conversions.byteArrayToInt(intBuffer);
        byte[] ciphertext = ciphertextBuffer.get(length);

        StreamUtil.readFully(inputStream, ciphertext, length);

        try {
          cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(ivBuffer));
          byte[] plaintext = cipher.doFinal(ciphertext, 0, length);

          return new String(plaintext);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
          throw new AssertionError(e);
        }
      } catch (EOFException e) {
        return null;
      }
    }
  }
}
