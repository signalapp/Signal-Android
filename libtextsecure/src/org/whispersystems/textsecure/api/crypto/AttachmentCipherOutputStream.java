package org.whispersystems.textsecure.api.crypto;

import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class AttachmentCipherOutputStream extends OutputStream {

  private final Cipher       cipher;
  private final Mac          mac;
  private final OutputStream outputStream;

  private long ciphertextLength = 0;

  public AttachmentCipherOutputStream(byte[] combinedKeyMaterial,
                                      OutputStream outputStream)
      throws IOException
  {
    try {
      this.outputStream = outputStream;
      this.cipher       = initializeCipher();
      this.mac          = initializeMac();

      byte[][] keyParts = Util.split(combinedKeyMaterial, 32, 32);

      this.cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyParts[0], "AES"));
      this.mac.init(new SecretKeySpec(keyParts[1], "HmacSHA256"));

      mac.update(cipher.getIV());
      outputStream.write(cipher.getIV());
      ciphertextLength += cipher.getIV().length;
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(byte[] buffer) throws IOException {
    write(buffer, 0, buffer.length);
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws IOException {
    byte[] ciphertext = cipher.update(buffer, offset, length);

    if (ciphertext != null) {
      mac.update(ciphertext);
      outputStream.write(ciphertext);
      ciphertextLength += ciphertext.length;
    }
  }

  @Override
  public void write(int b) {
    throw new AssertionError("NYI");
  }

  @Override
  public void flush() throws IOException {
    try {
      byte[] ciphertext = cipher.doFinal();
      byte[] auth       = mac.doFinal(ciphertext);

      outputStream.write(ciphertext);
      outputStream.write(auth);

      ciphertextLength += ciphertext.length;
      ciphertextLength += auth.length;

      outputStream.flush();
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  public static long getCiphertextLength(long plaintextLength) {
    return 16 + (((plaintextLength / 16) +1) * 16) + 32;
  }

  private Mac initializeMac() {
    try {
      return Mac.getInstance("HmacSHA256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private Cipher initializeCipher() {
    try {
      return Cipher.getInstance("AES/CBC/PKCS5Padding");
    } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
      throw new AssertionError(e);
    }
  }
}
