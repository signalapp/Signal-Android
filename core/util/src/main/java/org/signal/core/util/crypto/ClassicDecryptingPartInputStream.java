/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.core.util.crypto;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.core.util.stream.LimitedInputStream;
import org.signal.core.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ClassicDecryptingPartInputStream {

  private static final String TAG = Log.tag(ClassicDecryptingPartInputStream.class);

  private static final int IV_LENGTH  = 16;
  private static final int MAC_LENGTH = 20;

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull File file)
      throws IOException
  {
    try {
      if (file.length() <= IV_LENGTH + MAC_LENGTH) {
        throw new IOException("File too short");
      }

      verifyMac(attachmentSecret, file);

      FileInputStream fileStream = new FileInputStream(file);
      byte[]          ivBytes    = new byte[IV_LENGTH];
      readFully(fileStream, ivBytes);

      Cipher          cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      IvParameterSpec iv     = new IvParameterSpec(ivBytes);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(attachmentSecret.getClassicCipherKey(), "AES"), iv);

      return new CipherInputStreamWrapper(new LimitedInputStream(fileStream, file.length() - MAC_LENGTH - IV_LENGTH), cipher);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  private static void verifyMac(AttachmentSecret attachmentSecret, File file) throws IOException {
    Mac             mac        = initializeMac(new SecretKeySpec(attachmentSecret.getClassicMacKey(), "HmacSHA1"));
    FileInputStream macStream  = new FileInputStream(file);
    InputStream     dataStream = new LimitedInputStream(new FileInputStream(file), file.length() - MAC_LENGTH);
    byte[]          theirMac   = new byte[MAC_LENGTH];

    if (macStream.skip(file.length() - MAC_LENGTH) != file.length() - MAC_LENGTH) {
      throw new IOException("Unable to seek");
    }

    readFully(macStream, theirMac);

    byte[] buffer = new byte[4096];
    int    read;

    while ((read = dataStream.read(buffer)) != -1) {
      mac.update(buffer, 0, read);
    }

    byte[] ourMac = mac.doFinal();

    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw new IOException("Bad MAC");
    }

    macStream.close();
    dataStream.close();
  }

  private static Mac initializeMac(SecretKeySpec key) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA1");
      hmac.init(key);

      return hmac;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private static void readFully(InputStream in, byte[] buffer) throws IOException {
    int offset = 0;

    for (;;) {
      int read = in.read(buffer, offset, buffer.length-offset);

      if (read + offset < buffer.length) offset += read;
      else                               return;
    }
  }

  // Note (4/3/17) -- Older versions of Android have a busted OpenSSL provider that
  // throws a RuntimeException on a BadPaddingException, so we have to catch
  // that here in case someone calls close() before reaching the end of the
  // stream (since close() implicitly calls doFinal())
  //
  // See Signal-Android Issue #6477
  // Android: https://android-review.googlesource.com/#/c/65321/
  private static class CipherInputStreamWrapper extends CipherInputStream {

    CipherInputStreamWrapper(InputStream is, Cipher c) {
      super(is, c);
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }

    @Override
    public long skip(long skipAmount)
        throws IOException
    {
      long remaining = skipAmount;

      if (skipAmount <= 0) {
        return 0;
      }

      byte[] skipBuffer = new byte[4092];

      while (remaining > 0) {
        int read = super.read(skipBuffer, 0, Util.toIntExact(Math.min(skipBuffer.length, remaining)));

        if (read < 0) {
          break;
        }

        remaining -= read;
      }

      return skipAmount - remaining;
    }
  }
}
