package org.whispersystems.signalservice.api.crypto;


import org.whispersystems.signalservice.internal.util.Util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ProfileCipherInputStream extends FilterInputStream {

  private final Cipher cipher;

  private boolean finished = false;

  public ProfileCipherInputStream(InputStream in, byte[] key) throws IOException {
    super(in);

    try {
      this.cipher = Cipher.getInstance("AES/GCM/NoPadding");

      byte[] nonce = new byte[12];
      Util.readFully(in, nonce);

      this.cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  @Override
  public int read() {
    throw new AssertionError("Not supported!");
  }

  @Override
  public int read(byte[] input) throws IOException {
    return read(input, 0, input.length);
  }

  @Override
  public int read(byte[] output, int outputOffset, int outputLength) throws IOException {
    if (finished) return -1;

    try {
      byte[] ciphertext = new byte[outputLength / 2];
      int    read       = in.read(ciphertext, 0, ciphertext.length);

      if (read == -1) {
        if (cipher.getOutputSize(0) > outputLength) {
          throw new AssertionError("Need: " + cipher.getOutputSize(0) + " but only have: " + outputLength);
        }

        finished = true;
        return cipher.doFinal(output, outputOffset);
      } else {
        if (cipher.getOutputSize(read) > outputLength) {
          throw new AssertionError("Need: " + cipher.getOutputSize(read) + " but only have: " + outputLength);
        }

        return cipher.update(ciphertext, 0, read, output, outputOffset);
      }
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch(ShortBufferException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new IOException(e);
    }
  }

}
