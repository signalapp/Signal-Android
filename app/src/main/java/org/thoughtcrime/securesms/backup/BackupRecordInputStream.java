package org.thoughtcrime.securesms.backup;

import androidx.annotation.NonNull;

import org.signal.core.util.Conversions;
import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.backup.proto.BackupFrame;
import org.thoughtcrime.securesms.backup.proto.Header;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class BackupRecordInputStream extends FullBackupBase.BackupStream {

  private final String TAG             = Log.tag(BackupRecordInputStream.class);
  private final int    MAX_BUFFER_SIZE = 8192;

  private final int         version;
  private final InputStream in;
  private final Cipher      cipher;
  private final Mac         mac;

  private final byte[] cipherKey;

  private final byte[] iv;
  private       int    counter;

  BackupRecordInputStream(@NonNull InputStream in, @NonNull String passphrase) throws IOException {
    try {
      this.in = in;

      byte[] headerLengthBytes = new byte[4];
      StreamUtil.readFully(in, headerLengthBytes);

      int    headerLength = Conversions.byteArrayToInt(headerLengthBytes);
      byte[] headerFrame  = new byte[headerLength];
      StreamUtil.readFully(in, headerFrame);

      BackupFrame frame = BackupFrame.ADAPTER.decode(headerFrame);

      if (frame.header_ == null) {
        throw new IOException("Backup stream does not start with header!");
      }

      Header header = frame.header_;

      if (header.iv == null) {
        throw new IOException("Missing IV!");
      }

      this.iv = header.iv.toByteArray();

      if (iv.length != 16) {
        throw new IOException("Invalid IV length!");
      }

      this.version = header.version != null ? header.version : 0;
      if (!BackupVersions.isCompatible(version)) {
        throw new IOException("Invalid backup version: " + version);
      }

      byte[]   key     = getBackupKey(passphrase, header.salt != null ? header.salt.toByteArray() : null);
      byte[]   derived = HKDF.deriveSecrets(key, "Backup Export".getBytes(), 64);
      byte[][] split   = ByteUtil.split(derived, 32, 32);

      this.cipherKey = split[0];
      byte[] macKey  = split[1];

      this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
      this.mac    = Mac.getInstance("HmacSHA256");
      this.mac.init(new SecretKeySpec(macKey, "HmacSHA256"));

      this.counter = Conversions.byteArrayToInt(iv);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  BackupFrame readFrame() throws IOException {
    return readFrame(in);
  }

  boolean validateFrame() throws InvalidAlgorithmParameterException, IOException, InvalidKeyException {
    int frameLength = decryptFrameLength(in);
    if (frameLength <= 0) {
      Log.i(TAG, "Backup frame is not valid due to negative frame length. This is likely because the decryption passphrase was wrong.");
      return false;
    }

    int    bufferSize = Math.min(MAX_BUFFER_SIZE, frameLength);
    byte[] buffer     = new byte[bufferSize];
    byte[] theirMac   = new byte[10];
    while (frameLength > 0) {
      int read = in.read(buffer, 0, Math.min(buffer.length, frameLength));
      if (read == -1) return false;

      if (read < MAX_BUFFER_SIZE) {
        final int frameEndIndex = read - 10;
        mac.update(buffer, 0, frameEndIndex);
        System.arraycopy(buffer, frameEndIndex, theirMac, 0, theirMac.length);
      } else {
        mac.update(buffer, 0, read);
      }
      frameLength -= read;
    }
    
    byte[] ourMac = ByteUtil.trim(mac.doFinal(), 10);

    return MessageDigest.isEqual(ourMac, theirMac);
  }

  void readAttachmentTo(OutputStream out, int length) throws IOException {
    try {
      Conversions.intToByteArray(iv, 0, counter++);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));
      mac.update(iv);

      byte[] buffer = new byte[8192];

      while (length > 0) {
        int read = in.read(buffer, 0, Math.min(buffer.length, length));
        if (read == -1) throw new IOException("File ended early!");

        mac.update(buffer, 0, read);

        byte[] plaintext = cipher.update(buffer, 0, read);

        if (plaintext != null) {
          out.write(plaintext, 0, plaintext.length);
        }

        length -= read;
      }

      byte[] plaintext = cipher.doFinal();

      if (plaintext != null) {
        out.write(plaintext, 0, plaintext.length);
      }

      out.close();

      byte[] ourMac   = ByteUtil.trim(mac.doFinal(), 10);
      byte[] theirMac = new byte[10];

      try {
        StreamUtil.readFully(in, theirMac);
      } catch (IOException e) {
        throw new IOException(e);
      }

      if (!MessageDigest.isEqual(ourMac, theirMac)) {
        throw new BadMacException();
      }
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private BackupFrame readFrame(InputStream in) throws IOException {
    try {
      int frameLength = decryptFrameLength(in);

      byte[] frame = new byte[frameLength];
      StreamUtil.readFully(in, frame);

      byte[] theirMac = new byte[10];
      System.arraycopy(frame, frame.length - 10, theirMac, 0, theirMac.length);

      mac.update(frame, 0, frame.length - 10);
      byte[] ourMac = ByteUtil.trim(mac.doFinal(), 10);

      if (!MessageDigest.isEqual(ourMac, theirMac)) {
        throw new IOException("Bad MAC");
      }

      byte[] plaintext = cipher.doFinal(frame, 0, frame.length - 10);

      return BackupFrame.ADAPTER.decode(plaintext);
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private int decryptFrameLength(InputStream inputStream) throws IOException, InvalidAlgorithmParameterException, InvalidKeyException {
    byte[] length = new byte[4];
    StreamUtil.readFully(inputStream, length);

    Conversions.intToByteArray(iv, 0, counter++);
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

    int frameLength;
    if (BackupVersions.isFrameLengthEncrypted(version)) {
      mac.update(length);
      // this depends upon cipher being a stream cipher mode in order to get back the length without needing a full AES block-size input
      byte[] decryptedLength = cipher.update(length);
      if (decryptedLength.length != length.length) {
        throw new IOException("Cipher was not a stream cipher!");
      }
      frameLength = Conversions.byteArrayToInt(decryptedLength);
    } else {
      frameLength = Conversions.byteArrayToInt(length);
    }
    return frameLength;
  }

  static class BadMacException extends IOException {}
}
