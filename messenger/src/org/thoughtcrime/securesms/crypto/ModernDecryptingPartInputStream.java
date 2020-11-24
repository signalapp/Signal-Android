package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.Conversions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ModernDecryptingPartInputStream {

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull byte[] random, @NonNull File file, long offset)
      throws IOException
  {
    return createFor(attachmentSecret, random, new FileInputStream(file), offset);
  }

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull File file, long offset)
      throws IOException
  {
    FileInputStream inputStream = new FileInputStream(file);
    byte[]          random      = new byte[32];

    readFully(inputStream, random);

    return createFor(attachmentSecret, random, inputStream, offset);
  }

  private static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull byte[] random, @NonNull InputStream inputStream, long offset) throws IOException {
    try {
      Mac mac  = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(attachmentSecret.getModernKey(), "HmacSHA256"));

      byte[] iv        = new byte[16];
      int    remainder = (int) (offset % 16);
      Conversions.longTo4ByteArray(iv, 12, offset / 16);

      byte[] key    = mac.doFinal(random);
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      long skipped = inputStream.skip(offset - remainder);

      if (skipped != offset - remainder) {
        throw new IOException("Skip failed: " + skipped + " vs " + (offset - remainder));
      }

      CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
      byte[]            remainderBuffer   = new byte[remainder];

      readFully(cipherInputStream, remainderBuffer);

      return cipherInputStream;
    } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
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

}
