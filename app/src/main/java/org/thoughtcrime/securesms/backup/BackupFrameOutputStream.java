/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup;

import androidx.annotation.NonNull;

import org.signal.core.util.Conversions;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.kdf.HKDF;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.backup.proto.Attachment;
import org.thoughtcrime.securesms.backup.proto.Avatar;
import org.thoughtcrime.securesms.backup.proto.BackupFrame;
import org.thoughtcrime.securesms.backup.proto.DatabaseVersion;
import org.thoughtcrime.securesms.backup.proto.Header;
import org.thoughtcrime.securesms.backup.proto.KeyValue;
import org.thoughtcrime.securesms.backup.proto.SharedPreference;
import org.thoughtcrime.securesms.backup.proto.SqlStatement;
import org.thoughtcrime.securesms.backup.proto.Sticker;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class BackupFrameOutputStream extends FullBackupBase.BackupStream {

  private static final String TAG = Log.tag(BackupFrameOutputStream.class);

  private final OutputStream outputStream;
  private final Cipher       cipher;
  private final Mac          mac;

  private final byte[] cipherKey;
  private final byte[] iv;
  private       int    counter;

  private int frames;

  BackupFrameOutputStream(@NonNull OutputStream output, @NonNull String passphrase) throws IOException {
    try {
      byte[]   salt    = Util.getSecretBytes(32);
      byte[]   key     = getBackupKey(passphrase, salt);
      byte[]   derived = HKDF.deriveSecrets(key, "Backup Export".getBytes(), 64);
      byte[][] split   = ByteUtil.split(derived, 32, 32);

      this.cipherKey = split[0];
      byte[] macKey = split[1];

      this.cipher       = Cipher.getInstance("AES/CTR/NoPadding");
      this.mac          = Mac.getInstance("HmacSHA256");
      this.outputStream = output;
      this.iv           = Util.getSecretBytes(16);
      this.counter      = Conversions.byteArrayToInt(iv);

      mac.init(new SecretKeySpec(macKey, "HmacSHA256"));

      byte[] header = new BackupFrame.Builder().header_(new Header.Builder()
                                                            .iv(new okio.ByteString(iv))
                                                            .salt(new okio.ByteString(salt))
                                                            .version(BackupVersions.CURRENT_VERSION)
                                                            .build())
                                               .build()
                                               .encode();

      outputStream.write(Conversions.intToByteArray(header.length));
      outputStream.write(header);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public void write(SharedPreference preference) throws IOException {
    write(outputStream, new BackupFrame.Builder().preference(preference).build());
  }

  public void write(KeyValue keyValue) throws IOException {
    write(outputStream, new BackupFrame.Builder().keyValue(keyValue).build());
  }

  public void write(SqlStatement statement) throws IOException {
    write(outputStream, new BackupFrame.Builder().statement(statement).build());
  }

  public void write(@NonNull String avatarName, @NonNull InputStream in, long size) throws IOException {
    try {
      write(outputStream, new BackupFrame.Builder()
          .avatar(new Avatar.Builder()
                      .recipientId(avatarName)
                      .length(Util.toIntExact(size))
                      .build())
          .build());
    } catch (ArithmeticException e) {
      Log.w(TAG, "Unable to write avatar to backup", e);
      throw new FullBackupExporter.InvalidBackupStreamException();
    }

    if (writeStream(in) != size) {
      throw new IOException("Size mismatch!");
    }
  }

  public void write(@NonNull AttachmentId attachmentId, @NonNull InputStream in, long size) throws IOException {
    try {
      write(outputStream, new BackupFrame.Builder()
          .attachment(new Attachment.Builder()
                          .rowId(attachmentId.id)
                          .length(Util.toIntExact(size))
                          .build())
          .build());
    } catch (ArithmeticException e) {
      Log.w(TAG, "Unable to write " + attachmentId + " to backup", e);
      throw new FullBackupExporter.InvalidBackupStreamException();
    }

    if (writeStream(in) != size) {
      throw new IOException("Size mismatch!");
    }
  }

  public void writeSticker(long rowId, @NonNull InputStream in, long size) throws IOException {
    try {
      write(outputStream, new BackupFrame.Builder()
          .sticker(new Sticker.Builder()
                       .rowId(rowId)
                       .length(Util.toIntExact(size))
                       .build())
          .build());
    } catch (ArithmeticException e) {
      Log.w(TAG, "Unable to write sticker to backup", e);
      throw new FullBackupExporter.InvalidBackupStreamException();
    }

    if (writeStream(in) != size) {
      throw new IOException("Size mismatch!");
    }
  }

  void writeDatabaseVersion(int version) throws IOException {
    write(outputStream, new BackupFrame.Builder()
        .version(new DatabaseVersion.Builder().version(version).build())
        .build());
  }

  void writeEnd() throws IOException {
    write(outputStream, new BackupFrame.Builder().end(true).build());
  }

  /**
   * @return The amount of data written from the provided InputStream.
   */
  private long writeStream(@NonNull InputStream inputStream) throws IOException {
    try {
      Conversions.intToByteArray(iv, 0, counter++);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));
      mac.update(iv);

      byte[] buffer = new byte[8192];
      long   total  = 0;

      int read;

      while ((read = inputStream.read(buffer)) != -1) {
        byte[] ciphertext = cipher.update(buffer, 0, read);

        if (ciphertext != null) {
          outputStream.write(ciphertext);
          mac.update(ciphertext);
        }

        total += read;
      }

      byte[] remainder = cipher.doFinal();
      outputStream.write(remainder);
      mac.update(remainder);

      byte[] attachmentDigest = mac.doFinal();
      outputStream.write(attachmentDigest, 0, 10);

      return total;
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private void write(@NonNull OutputStream out, @NonNull BackupFrame frame) throws IOException {
    try {
      Conversions.intToByteArray(iv, 0, counter++);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

      byte[] encodedFrame = frame.encode();

      // this assumes a stream cipher
      byte[] length = Conversions.intToByteArray(encodedFrame.length + 10);
      if (BackupVersions.isFrameLengthEncrypted(BackupVersions.CURRENT_VERSION)) {
        byte[] encryptedLength = cipher.update(length);
        if (encryptedLength.length != length.length) {
          throw new IOException("Stream cipher assumption has been violated!");
        }
        mac.update(encryptedLength);
        length = encryptedLength;
      }

      byte[] frameCiphertext = cipher.doFinal(frame.encode());
      if (frameCiphertext.length != encodedFrame.length) {
        throw new IOException("Stream cipher assumption has been violated!");
      }

      byte[] frameMac = mac.doFinal(frameCiphertext);

      out.write(length);
      out.write(frameCiphertext);
      out.write(frameMac, 0, 10);
      frames++;
    } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  public void close() throws IOException {
    outputStream.flush();
    outputStream.close();
  }

  public int getFrames() {
    return frames;
  }
}
