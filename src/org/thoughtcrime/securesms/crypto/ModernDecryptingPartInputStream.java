package org.thoughtcrime.securesms.crypto;


import android.support.annotation.NonNull;

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

  public static InputStream createFor(@NonNull AttachmentSecret attachmentSecret, @NonNull byte[] random, @NonNull File file)
      throws IOException
  {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(attachmentSecret.getModernKey(), "HmacSHA256"));

      FileInputStream fileInputStream = new FileInputStream(file);
      byte[]          iv              = new byte[16];
      byte[]          key             = mac.doFinal(random);

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

      return new CipherInputStream(fileInputStream, cipher);
    } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
      throw new AssertionError(e);
    }
  }

}
