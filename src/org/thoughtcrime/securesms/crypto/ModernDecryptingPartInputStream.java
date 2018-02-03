package org.thoughtcrime.securesms.crypto;


import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull byte[] random, @NonNull File file)
      throws IOException
  {
    return createFor(attachmentSecret, random, new FileInputStream(file));
  }

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull File file)
      throws IOException
  {
    FileInputStream inputStream = new FileInputStream(file);
    byte[]          random      = new byte[32];

    readFully(inputStream, random);

    return createFor(attachmentSecret, random, inputStream);
  }

  private static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull byte[] random, @NonNull InputStream inputStream) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(attachmentSecret.getModernKey(), "HmacSHA256"));

      byte[] iv  = new byte[16];
      byte[] key = mac.doFinal(random);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      return new CipherInputStream(inputStream, cipher);
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
