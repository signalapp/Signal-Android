package org.thoughtcrime.securesms.backup;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.backup.EncryptedBackupProtos.EncryptedBackupHeader;
import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class EncryptedBackup {
  private final static String TAG = "EncryptedBackup";

  public static OutputStream getOutputStream(Context context, File outFile, MasterSecret masterSecret)
      throws IOException
  {
    FileOutputStream plaintextStream = new FileOutputStream(outFile);

    EncryptedBackupHeader header = EncryptedBackupHeader.newBuilder()
                                                        .setEncryptedMasterSecret(ByteString.copyFrom(MasterSecretUtil.getEncryptedMasterSecret(context)))
                                                        .setKdfIterations(MasterSecretUtil.getIterationCount(context))
                                                        .setEncryptionSalt(ByteString.copyFrom(MasterSecretUtil.getEncryptionSalt(context)))
                                                        .setMacSalt(ByteString.copyFrom(MasterSecretUtil.getMacSalt(context)))
                                                        .build();
    Log.w(TAG, "writing header of size " + header.getSerializedSize());
    byte[] headerSize = ByteBuffer.allocate(4).putInt(header.getSerializedSize()).array();

    plaintextStream.write(headerSize);
    plaintextStream.write(header.toByteArray());

    return new EncryptingPartOutputStream(outFile, masterSecret, true);
  }

  public static InputStream getInputStream(Context context, File inFile, String passphrase)
      throws IOException, InvalidPassphraseException
  {
    FileInputStream inputStream = new FileInputStream(inFile);
    byte[] headerSizeBytes = new byte[4];
    if (inputStream.read(headerSizeBytes) != headerSizeBytes.length) {
      throw new IOException("incomplete header length, malformed encrypted backup");
    }
    int headerSize = ByteBuffer.wrap(headerSizeBytes).getInt();
    Log.w(TAG, "read header size of " + headerSize);

    byte[] headerBytes = new byte[headerSize];
    if (inputStream.read(headerBytes) != headerSize) {
      throw new IOException("incomplete header, malformed encrypted backup");
    }

    EncryptedBackupHeader header = EncryptedBackupHeader.parseFrom(headerBytes);

    MasterSecret backupSecret = MasterSecretUtil.getMasterSecret(context,
                                                                 passphrase,
                                                                 header.getKdfIterations(),
                                                                 header.getEncryptedMasterSecret().toByteArray(),
                                                                 header.getMacSalt().toByteArray(),
                                                                 header.getEncryptionSalt().toByteArray());

    return new DecryptingPartInputStream(inFile, backupSecret, headerSizeBytes.length + headerSize);
  }
}
