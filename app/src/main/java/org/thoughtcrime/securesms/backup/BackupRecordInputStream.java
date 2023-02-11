package org.thoughtcrime.securesms.backup;

import androidx.annotation.NonNull;

import org.signal.core.util.Conversions;
import org.signal.core.util.StreamUtil;
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

      this.iv = header.iv.toByteArray();

      if (iv.length != 16) {
        throw new IOException("Invalid IV length!");
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
      byte[] length = new byte[4];
      StreamUtil.readFully(in, length);

      byte[] frame = new byte[Conversions.byteArrayToInt(length)];
      StreamUtil.readFully(in, frame);

      byte[] theirMac = new byte[10];
      System.arraycopy(frame, frame.length - 10, theirMac, 0, theirMac.length);

      mac.update(frame, 0, frame.length - 10);
      byte[] ourMac = ByteUtil.trim(mac.doFinal(), 10);

      if (!MessageDigest.isEqual(ourMac, theirMac)) {
        throw new IOException("Bad MAC");
      }

      Conversions.intToByteArray(iv, 0, counter++);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

      byte[] plaintext = cipher.doFinal(frame, 0, frame.length - 10);

      return BackupFrame.ADAPTER.decode(plaintext);
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  static class BadMacException extends IOException {}
}
