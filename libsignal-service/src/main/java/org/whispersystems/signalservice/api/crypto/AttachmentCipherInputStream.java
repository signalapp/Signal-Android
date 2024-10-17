/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

import org.signal.core.util.stream.LimitedInputStream;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice;
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacInputStream;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.whispersystems.signalservice.api.backup.BackupKey;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayInputStream;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

  private final Cipher cipher;
  private final long   totalDataSize;

  private boolean done;
  private long    totalRead;
  private byte[]  overflowBuffer;

  /**
   * Passing in a null incrementalDigest and/or 0 for the chunk size at the call site disables incremental mac validation.
   */
  public static LimitedInputStream createForAttachment(File file, long plaintextLength, byte[] combinedKeyMaterial, byte[] digest, byte[] incrementalDigest, int incrementalMacChunkSize)
      throws InvalidMessageException, IOException {
    return createForAttachment(file, plaintextLength, combinedKeyMaterial, digest, incrementalDigest, incrementalMacChunkSize, false);
  }

  /**
   * Passing in a null incrementalDigest and/or 0 for the chunk size at the call site disables incremental mac validation.
   *
   * Passing in true for ignoreDigest DOES NOT VERIFY THE DIGEST
   */
  public static LimitedInputStream createForAttachment(File file, long plaintextLength, byte[] combinedKeyMaterial, byte[] digest, byte[] incrementalDigest, int incrementalMacChunkSize, boolean ignoreDigest)
      throws InvalidMessageException, IOException
  {
    return createForAttachment(() -> new FileInputStream(file), file.length(), plaintextLength, combinedKeyMaterial, digest, incrementalDigest, incrementalMacChunkSize, ignoreDigest);
  }

  /**
   * Passing in a null incrementalDigest and/or 0 for the chunk size at the call site disables incremental mac validation.
   *
   * Passing in true for ignoreDigest DOES NOT VERIFY THE DIGEST
   */
  public static LimitedInputStream createForAttachment(StreamSupplier streamSupplier, long streamLength, long plaintextLength, byte[] combinedKeyMaterial, byte[] digest, byte[] incrementalDigest, int incrementalMacChunkSize, boolean ignoreDigest)
      throws InvalidMessageException, IOException
  {
    byte[][] parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE);
    Mac      mac   = initMac(parts[1]);

    if (streamLength <= BLOCK_SIZE + mac.getMacLength()) {
      throw new InvalidMessageException("Message shorter than crypto overhead! length: " + streamLength);
    }

    if (!ignoreDigest && digest == null) {
      throw new InvalidMessageException("Missing digest!");
    }

    final InputStream wrappedStream;
    final boolean     hasIncrementalMac = incrementalDigest != null && incrementalDigest.length > 0 && incrementalMacChunkSize > 0;

    if (!hasIncrementalMac) {
      try (InputStream macVerificationStream = streamSupplier.openStream()) {
        verifyMac(macVerificationStream, streamLength, mac, digest);
      }
      wrappedStream = streamSupplier.openStream();
    } else {
      wrappedStream = new IncrementalMacInputStream(
          new IncrementalMacAdditionalValidationsInputStream(
              streamSupplier.openStream(),
              streamLength,
              mac,
              digest
          ),
          parts[1],
          ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
          incrementalDigest);
    }
    InputStream inputStream = new AttachmentCipherInputStream(wrappedStream, parts[0], streamLength - BLOCK_SIZE - mac.getMacLength());

    if (plaintextLength != 0) {
      return new LimitedInputStream(inputStream, plaintextLength);
    } else {
      return LimitedInputStream.withoutLimits(inputStream);
    }
  }

  /**
   * Decrypt archived media to it's original attachment encrypted blob.
   */
  public static LimitedInputStream createForArchivedMedia(BackupKey.MediaKeyMaterial archivedMediaKeyMaterial, File file, long originalCipherTextLength)
      throws InvalidMessageException, IOException
  {
    Mac mac = initMac(archivedMediaKeyMaterial.getMacKey());

    if (file.length() <= BLOCK_SIZE + mac.getMacLength()) {
      throw new InvalidMessageException("Message shorter than crypto overhead!");
    }

    try (FileInputStream macVerificationStream = new FileInputStream(file)) {
      verifyMac(macVerificationStream, file.length(), mac, null);
    }

    InputStream inputStream = new AttachmentCipherInputStream(new FileInputStream(file), archivedMediaKeyMaterial.getCipherKey(), file.length() - BLOCK_SIZE - mac.getMacLength());

    if (originalCipherTextLength != 0) {
      return new LimitedInputStream(inputStream, originalCipherTextLength);
    } else {
      return LimitedInputStream.withoutLimits(inputStream);
    }
  }

  public static LimitedInputStream createStreamingForArchivedAttachment(BackupKey.MediaKeyMaterial archivedMediaKeyMaterial, File file, long originalCipherTextLength, long plaintextLength, byte[] combinedKeyMaterial, byte[] digest, byte[] incrementalDigest, int incrementalMacChunkSize)
      throws InvalidMessageException, IOException
  {
    final InputStream archiveStream = createForArchivedMedia(archivedMediaKeyMaterial, file, originalCipherTextLength);

    byte[][] parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE);
    Mac      mac   = initMac(parts[1]);

    if (originalCipherTextLength <= BLOCK_SIZE + mac.getMacLength()) {
      throw new InvalidMessageException("Message shorter than crypto overhead!");
    }

    if (digest == null) {
      throw new InvalidMessageException("Missing digest!");
    }

    final InputStream wrappedStream;
      wrappedStream = new IncrementalMacInputStream(
          new IncrementalMacAdditionalValidationsInputStream(
              archiveStream,
              file.length(),
              mac,
              digest
          ),
          parts[1],
          ChunkSizeChoice.everyNthByte(incrementalMacChunkSize),
          incrementalDigest);

    InputStream inputStream = new AttachmentCipherInputStream(wrappedStream, parts[0], file.length() - BLOCK_SIZE - mac.getMacLength());

    if (plaintextLength != 0) {
      return new LimitedInputStream(inputStream, plaintextLength);
    } else {
      return LimitedInputStream.withoutLimits(inputStream);
    }

  }

  public static InputStream createForStickerData(byte[] data, byte[] packKey)
      throws InvalidMessageException, IOException
  {
    byte[]   combinedKeyMaterial = HKDF.deriveSecrets(packKey, "Sticker Pack".getBytes(), 64);
    byte[][] parts               = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE);
    Mac      mac                 = initMac(parts[1]);

    if (data.length <= BLOCK_SIZE + mac.getMacLength()) {
      throw new InvalidMessageException("Message shorter than crypto overhead!");
    }

    try (InputStream inputStream = new ByteArrayInputStream(data)) {
      verifyMac(inputStream, data.length, mac, null);
    }

    return new AttachmentCipherInputStream(new ByteArrayInputStream(data), parts[0], data.length - BLOCK_SIZE - mac.getMacLength());
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
    } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public int read() throws IOException {
    byte[] buffer = new byte[1];
    int    read;

    //noinspection StatementWithEmptyBody
    while ((read = read(buffer)) == 0) ;

    return (read == -1) ? -1 : ((int) buffer[0]) & 0xFF;
  }

  @Override
  public int read(@Nonnull byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(@Nonnull byte[] buffer, int offset, int length) throws IOException {
    if (totalRead != totalDataSize) {
      return readIncremental(buffer, offset, length);
    } else if (!done) {
      return readFinal(buffer, offset, length);
    } else {
      return -1;
    }
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public long skip(long byteCount) throws IOException {
    long skipped = 0L;
    while (skipped < byteCount) {
      byte[] buf  = new byte[Math.min(4096, (int) (byteCount - skipped))];
      int    read = read(buf);

      skipped += read;
    }

    return skipped;
  }

  private int readFinal(byte[] buffer, int offset, int length) throws IOException {
    try {
      byte[] internal     = new byte[buffer.length];
      int    actualLength = Math.min(length, cipher.doFinal(internal, 0));
      System.arraycopy(internal, 0, buffer, offset, actualLength);

      done = true;
      return actualLength;
    } catch (IllegalBlockSizeException | BadPaddingException | ShortBufferException e) {
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
      length = (int) (totalDataSize - totalRead);

    byte[] internalBuffer = new byte[length];
    int    read           = super.read(internalBuffer, 0, internalBuffer.length <= cipher.getBlockSize() ? internalBuffer.length : internalBuffer.length - cipher.getBlockSize());
    totalRead += read;

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

  private static Mac initMac(byte[] key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private static void verifyMac(@Nonnull InputStream inputStream, long length, @Nonnull Mac mac, @Nullable byte[] theirDigest)
      throws InvalidMessageException
  {
    try {
      MessageDigest digest        = MessageDigest.getInstance("SHA256");
      int           remainingData = Util.toIntExact(length) - mac.getMacLength();
      byte[]        buffer        = new byte[4096];

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
        throw new InvalidMessageException("MAC doesn't match!");
      }

      byte[] ourDigest = digest.digest(theirMac);

      if (theirDigest != null && !MessageDigest.isEqual(ourDigest, theirDigest)) {
        throw new InvalidMessageException("Digest doesn't match!");
      }

    } catch (IOException | ArithmeticException e1) {
      throw new InvalidMessageException(e1);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private void readFully(byte[] buffer) throws IOException {
    int offset = 0;

    for (; ; ) {
      int read = super.read(buffer, offset, buffer.length - offset);

      if (read + offset < buffer.length) {
        offset += read;
      } else {
        return;
      }
    }
  }

  public interface StreamSupplier {
    @Nonnull InputStream openStream() throws IOException;
  }
}
