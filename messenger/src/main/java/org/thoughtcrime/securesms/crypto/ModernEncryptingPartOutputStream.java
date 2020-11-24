package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Constructs an OutputStream that encrypts data written to it with the AttachmentSecret provided.
 *
 * The on-disk format is very simple, and intentionally no longer includes authentication.
 */
public class ModernEncryptingPartOutputStream {

  public static Pair<byte[], OutputStream> createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull File file, boolean inline)
      throws IOException
  {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(attachmentSecret.getModernKey(), "HmacSHA256"));

      FileOutputStream fileOutputStream = new FileOutputStream(file);
      byte[]           iv               = new byte[16];
      byte[]           key              = mac.doFinal(random);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      if (inline) {
        fileOutputStream.write(random);
      }

      return new Pair<>(random, new CipherOutputStream(fileOutputStream, cipher));
    } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
      throw new AssertionError(e);
    }
  }

}
