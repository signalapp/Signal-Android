/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.whispersystems.textsecure.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class for streaming an encrypted push attachment off disk.
 *
 * @author Moxie Marlinspike
 */

public class AttachmentCipherInputStream extends FileInputStream {

  private static final int BLOCK_SIZE = 16;

  private Cipher  cipher;
  private boolean done;
  private long    totalDataSize;
  private long    totalRead;
  private byte[] overflowBuffer;

  public AttachmentCipherInputStream(File file, byte[] combinedKeyMaterial)
      throws IOException, InvalidMessageException
  {
    super(file);

    try {
      byte[][] parts = Util.split(combinedKeyMaterial,
                                  AttachmentCipher.CIPHER_KEY_SIZE,
                                  AttachmentCipher.MAC_KEY_SIZE);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(parts[1], "HmacSHA256"));

      if (file.length() <= BLOCK_SIZE + mac.getMacLength()) {
        throw new InvalidMessageException("Message shorter than crypto overhead!");
      }

      verifyMac(file, mac);

      byte[] iv = new byte[BLOCK_SIZE];
      readFully(iv);

      this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(parts[0], "AES"), new IvParameterSpec(iv));

      this.done          = false;
      this.totalRead     = 0;
      this.totalDataSize = file.length() - cipher.getBlockSize() - mac.getMacLength();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidMacException e) {
      throw new InvalidMessageException(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if      (totalRead != totalDataSize) return readIncremental(buffer, offset, length);
    else if (!done)                      return readFinal(buffer, offset, length);
    else                                 return -1;
  }

  private int readFinal(byte[] buffer, int offset, int length) throws IOException {
    try {
      int flourish = cipher.doFinal(buffer, offset);

      done = true;
      return flourish;
    } catch (IllegalBlockSizeException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Illegal block size exception!");
    } catch (ShortBufferException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Short buffer exception!");
    } catch (BadPaddingException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Bad padding exception!");
    }
  }

  private int readIncremental(byte[] buffer, int offset, int length) throws IOException {
    int readLength = 0;
    if (null != overflowBuffer) {
      if (overflowBuffer.length > length) {
        System.arraycopy(overflowBuffer, 0, buffer, offset, length);
        overflowBuffer = Arrays.copyOfRange(overflowBuffer, length, overflowBuffer.length);
        return length;
      } else if (overflowBuffer.length == length) {
        System.arraycopy(overflowBuffer, 0, buffer, offset, length);
        overflowBuffer = null;
        return length;
      } else {
        System.arraycopy(overflowBuffer, 0, buffer, offset, overflowBuffer.length);
        readLength += overflowBuffer.length;
        offset += readLength;
        length -= readLength;
        overflowBuffer = null;
      }
    }

    if (length + totalRead > totalDataSize)
      length = (int)(totalDataSize - totalRead);

    byte[] internalBuffer = new byte[length];
    int read              = super.read(internalBuffer, 0, internalBuffer.length <= cipher.getBlockSize() ? internalBuffer.length : internalBuffer.length - cipher.getBlockSize());
    totalRead            += read;

    try {
      int outputLen = cipher.getOutputSize(read);

      if (outputLen <= length) {
        readLength += cipher.update(internalBuffer, 0, read, buffer, offset);
        return readLength;
      }

      byte[] transientBuffer = new byte[outputLen];
      outputLen = cipher.update(internalBuffer, 0, read, transientBuffer, 0);
      if (outputLen <= length) {
        System.arraycopy(transientBuffer, 0, buffer, offset, outputLen);
        readLength += outputLen;
      } else {
        System.arraycopy(transientBuffer, 0, buffer, offset, length);
        overflowBuffer = Arrays.copyOfRange(transientBuffer, length, outputLen);
        readLength += length;
      }
      return readLength;
    } catch (ShortBufferException e) {
      throw new AssertionError(e);
    }
  }

  private void verifyMac(File file, Mac mac) throws FileNotFoundException, InvalidMacException {
    try {
      FileInputStream fin           = new FileInputStream(file);
      int             remainingData = (int) file.length() - mac.getMacLength();
      byte[]          buffer        = new byte[4096];

      while (remainingData > 0) {
        int read = fin.read(buffer, 0, Math.min(buffer.length, remainingData));
        mac.update(buffer, 0, read);
        remainingData -= read;
      }

      byte[] ourMac   = mac.doFinal();
      byte[] theirMac = new byte[mac.getMacLength()];
      Util.readFully(fin, theirMac);

      if (!Arrays.equals(ourMac, theirMac)) {
        throw new InvalidMacException("MAC doesn't match!");
      }
    } catch (IOException e1) {
      throw new InvalidMacException(e1);
    }
  }

  private void readFully(byte[] buffer) throws IOException {
    int offset = 0;

    for (;;) {
      int read = super.read(buffer, offset, buffer.length - offset);

      if (read + offset < buffer.length) offset += read;
      else                		           return;
    }
  }


}
