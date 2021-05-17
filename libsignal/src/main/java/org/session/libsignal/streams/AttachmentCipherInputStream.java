/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.streams;

import org.session.libsignal.exceptions.InvalidMacException;
import org.session.libsignal.exceptions.InvalidMessageException;
import org.session.libsignal.utilities.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
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

public class AttachmentCipherInputStream extends FilterInputStream {

  private static final int BLOCK_SIZE      = 16;
  private static final int CIPHER_KEY_SIZE = 32;
  private static final int MAC_KEY_SIZE    = 32;

  private Cipher  cipher;
  private boolean done;
  private long    totalDataSize;
  private long    totalRead;
  private byte[]  overflowBuffer;

  public static InputStream createForAttachment(File file, long plaintextLength, byte[] combinedKeyMaterial, byte[] digest)
      throws InvalidMessageException, IOException
  {
    try {
      byte[][] parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE);
      Mac      mac   = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(parts[1], "HmacSHA256"));

      if (file.length() <= BLOCK_SIZE + mac.getMacLength()) {
        throw new InvalidMessageException("Message shorter than crypto overhead!");
      }

      if (digest == null) {
        throw new InvalidMacException("Missing digest!");
      }

      FileInputStream fin = new FileInputStream(file);
      verifyMac(fin, file.length(), mac, digest);

      InputStream inputStream = new AttachmentCipherInputStream(new FileInputStream(file), parts[0], file.length() - BLOCK_SIZE - mac.getMacLength());

      if (plaintextLength != 0) {
        inputStream = new ContentLengthInputStream(inputStream, plaintextLength);
      }

      return inputStream;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidMacException e) {
      throw new InvalidMessageException(e);
    }
  }

  private AttachmentCipherInputStream(InputStream inputStream, byte[] cipherKey, long totalDataSize)
      throws IOException
  {
    super(inputStream);

    try {
      byte[] iv = new byte[BLOCK_SIZE];
      readFully(iv);

      this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

      this.done          = false;
      this.totalRead     = 0;
      this.totalDataSize = totalDataSize;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
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

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public long skip(long byteCount) throws IOException {
    long skipped = 0L;
    while (skipped < byteCount) {
      byte[] buf  = new byte[Math.min(4096, (int)(byteCount-skipped))];
      int    read = read(buf);

      skipped += read;
    }

    return skipped;
  }

  private int readFinal(byte[] buffer, int offset, int length) throws IOException {
    try {
      int flourish = cipher.doFinal(buffer, offset);

      done = true;
      return flourish;
    } catch (IllegalBlockSizeException e) {
      throw new IOException(e);
    } catch (BadPaddingException e) {
      throw new IOException(e);
    } catch (ShortBufferException e) {
      throw new IOException(e);
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

  private static void verifyMac(InputStream inputStream, long length, Mac mac, byte[] theirDigest)
      throws InvalidMacException
  {
    try {
      MessageDigest   digest        = MessageDigest.getInstance("SHA256");
      int             remainingData = Util.toIntExact(length) - mac.getMacLength();
      byte[]          buffer        = new byte[4096];

      while (remainingData > 0) {
        int read = inputStream.read(buffer, 0, Math.min(buffer.length, remainingData));
        mac.update(buffer, 0, read);
        digest.update(buffer, 0, read);
        remainingData -= read;
      }

      byte[] ourMac   = mac.doFinal();
      byte[] theirMac = new byte[mac.getMacLength()];
      Util.readFully(inputStream, theirMac);

      if (!MessageDigest.isEqual(ourMac, theirMac)) {
        throw new InvalidMacException("MAC doesn't match!");
      }

      byte[] ourDigest = digest.digest(theirMac);

      if (theirDigest != null && !MessageDigest.isEqual(ourDigest, theirDigest)) {
        throw new InvalidMacException("Digest doesn't match!");
      }

    } catch (IOException e) {
      throw new InvalidMacException(e);
    } catch (ArithmeticException e) {
      throw new InvalidMacException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
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
