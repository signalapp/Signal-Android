package org.thoughtcrime.securesms.backup;


import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.backup.BackupProtos.Attachment;
import org.thoughtcrime.securesms.backup.BackupProtos.BackupFrame;
import org.thoughtcrime.securesms.backup.BackupProtos.DatabaseVersion;
import org.thoughtcrime.securesms.backup.BackupProtos.SharedPreference;
import org.thoughtcrime.securesms.backup.BackupProtos.SqlStatement;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FullBackupImporter extends FullBackupBase {

  @SuppressWarnings("unused")
  private static final String TAG = FullBackupImporter.class.getSimpleName();

  public static void importFile(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret,
                                @NonNull SQLiteDatabase db, @NonNull File file, @NonNull String passphrase)
      throws IOException
  {
    byte[]                  key         = getBackupKey(passphrase);
    BackupRecordInputStream inputStream = new BackupRecordInputStream(file, key);
    int                     count       = 0;

    try {
      db.beginTransaction();

      BackupFrame frame;

      while (!(frame = inputStream.readFrame()).getEnd()) {
        if (count++ % 100 == 0) EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.PROGRESS, count));

        if      (frame.hasVersion())    processVersion(db, frame.getVersion());
        else if (frame.hasStatement())  processStatement(db, frame.getStatement());
        else if (frame.hasPreference()) processPreference(context, frame.getPreference());
        else if (frame.hasAttachment()) processAttachment(context, attachmentSecret, db, frame.getAttachment(), inputStream);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    EventBus.getDefault().post(new BackupEvent(BackupEvent.Type.FINISHED, count));
  }

  private static void processVersion(@NonNull SQLiteDatabase db, DatabaseVersion version) {
    db.setVersion(version.getVersion());
  }

  private static void processStatement(@NonNull SQLiteDatabase db, SqlStatement statement) {
    List<Object> parameters = new LinkedList<>();

    for (SqlStatement.SqlParameter parameter : statement.getParametersList()) {
      if      (parameter.hasStringParamter())   parameters.add(parameter.getStringParamter());
      else if (parameter.hasDoubleParameter())  parameters.add(parameter.getDoubleParameter());
      else if (parameter.hasIntegerParameter()) parameters.add(parameter.getIntegerParameter());
      else if (parameter.hasBlobParameter())    parameters.add(parameter.getBlobParameter().toByteArray());
      else if (parameter.hasNullparameter())    parameters.add(null);
    }

    if (parameters.size() > 0) db.execSQL(statement.getStatement(), parameters.toArray());
    else                       db.execSQL(statement.getStatement());
  }

  private static void processAttachment(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret, @NonNull SQLiteDatabase db, @NonNull Attachment attachment, BackupRecordInputStream inputStream)
      throws IOException
  {
    File partsDirectory = context.getDir(AttachmentDatabase.DIRECTORY, Context.MODE_PRIVATE);
    File dataFile       = File.createTempFile("part", ".mms", partsDirectory);

    Pair<byte[], OutputStream> output = ModernEncryptingPartOutputStream.createFor(attachmentSecret, dataFile, false);

    inputStream.readAttachmentTo(output.second, attachment.getLength());

    ContentValues contentValues = new ContentValues();
    contentValues.put(AttachmentDatabase.DATA, dataFile.getAbsolutePath());
    contentValues.put(AttachmentDatabase.THUMBNAIL, (String)null);
    contentValues.put(AttachmentDatabase.DATA_RANDOM, output.first);

    db.update(AttachmentDatabase.TABLE_NAME, contentValues,
              AttachmentDatabase.ROW_ID + " = ? AND " + AttachmentDatabase.UNIQUE_ID + " = ?",
              new String[] {String.valueOf(attachment.getRowId()), String.valueOf(attachment.getAttachmentId())});
  }

  @SuppressLint("ApplySharedPref")
  private static void processPreference(@NonNull Context context, SharedPreference preference) {
    SharedPreferences preferences = context.getSharedPreferences(preference.getFile(), 0);
    preferences.edit().putString(preference.getKey(), preference.getValue()).commit();
  }

  private static class BackupRecordInputStream {

    private final InputStream in;
    private final Cipher      cipher;
    private final Mac         mac;

    private final byte[] cipherKey;
    private final byte[] macKey;

    private byte[] iv;
    private int    counter;

    private BackupRecordInputStream(@NonNull File file, @NonNull byte[] key) throws IOException {
      try {
        byte[]   derived = new HKDFv3().deriveSecrets(key, "Backup Export".getBytes(), 64);
        byte[][] split   = ByteUtil.split(derived, 32, 32);

        this.cipherKey = split[0];
        this.macKey    = split[1];

        this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
        this.mac    = Mac.getInstance("HmacSHA256");
        this.in     = new FileInputStream(file);
        this.mac.init(new SecretKeySpec(macKey, "HmacSHA256"));

        byte[] headerLengthBytes = new byte[4];
        Util.readFully(in, headerLengthBytes);

        int headerLength = Conversions.byteArrayToInt(headerLengthBytes);
        byte[] headerFrame = new byte[headerLength];
        Util.readFully(in, headerFrame);

        BackupFrame frame = BackupFrame.parseFrom(headerFrame);

        if (!frame.hasHeader()) {
          throw new IOException("Backup stream does not start with header!");
        }

        BackupProtos.Header header = frame.getHeader();

        this.iv = header.getIv().toByteArray();

        if (iv.length != 16) {
          throw new IOException("Invalid IV length!");
        }

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
          out.write(plaintext, 0, plaintext.length);

          length -= read;
        }

        out.close();

        byte[] ourMac   = mac.doFinal();
        byte[] theirMac = new byte[10];

        try {
          Util.readFully(in, theirMac);
        } catch (IOException e) {
          //destination.delete();
          throw new IOException(e);
        }

        if (MessageDigest.isEqual(ourMac, theirMac)) {
          //destination.delete();
          throw new IOException("Bad MAC");
        }
      } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
        throw new AssertionError(e);
      }
    }

    private BackupFrame readFrame(InputStream in) throws IOException {
      try {
        byte[] length = new byte[4];
        Util.readFully(in, length);

        byte[] frame = new byte[Conversions.byteArrayToInt(length)];
        Util.readFully(in, frame);

        byte[] theirMac = new byte[10];
        System.arraycopy(frame, frame.length - 10, theirMac, 0, theirMac.length);

        mac.update(frame, 0, frame.length - 10);
        byte[] ourMac = mac.doFinal();

        if (MessageDigest.isEqual(ourMac, theirMac)) {
          throw new IOException("Bad MAC");
        }

        Conversions.intToByteArray(iv, 0, counter++);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));

        byte[] plaintext = cipher.doFinal(frame, 0, frame.length - 10);

        return BackupFrame.parseFrom(plaintext);
      } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
        throw new AssertionError(e);
      }
    }
  }

}
