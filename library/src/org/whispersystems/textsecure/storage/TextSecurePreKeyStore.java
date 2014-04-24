package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.util.Conversions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TextSecurePreKeyStore implements PreKeyStore {

  public  static final String PREKEY_DIRECTORY       = "prekeys";
  private static final int    CURRENT_VERSION_MARKER = 1;
  private static final String TAG                    = TextSecurePreKeyStore.class.getSimpleName();

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecurePreKeyStore(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public PreKeyRecord load(int preKeyId) throws InvalidKeyIdException {
    try {
      MasterCipher    masterCipher  = new MasterCipher(masterSecret);
      FileInputStream fin           = new FileInputStream(getPreKeyFile(preKeyId));
      int             recordVersion = readInteger(fin);

      if (recordVersion != CURRENT_VERSION_MARKER) {
        throw new AssertionError("Invalid version: " + recordVersion);
      }

      byte[] serializedRecord = masterCipher.decryptBytes(readBlob(fin));
      return new PreKeyRecord(serializedRecord);

    } catch (IOException | InvalidMessageException e) {
      Log.w(TAG, e);
      throw new InvalidKeyIdException(e);
    }
  }

  @Override
  public void store(int preKeyId, PreKeyRecord record) {
    try {
      MasterCipher     masterCipher = new MasterCipher(masterSecret);
      RandomAccessFile recordFile   = new RandomAccessFile(getPreKeyFile(preKeyId), "rw");
      FileChannel      out          = recordFile.getChannel();

      out.position(0);
      writeInteger(CURRENT_VERSION_MARKER, out);
      writeBlob(masterCipher.encryptBytes(record.serialize()), out);
      out.truncate(out.position());

      recordFile.close();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public boolean contains(int preKeyId) {
    File record = getPreKeyFile(preKeyId);
    return record.exists();
  }

  @Override
  public void remove(int preKeyId) {
    File record = getPreKeyFile(preKeyId);
    record.delete();
  }

  private File getPreKeyFile(int preKeyId) {
    return new File(getPreKeyDirectory(), String.valueOf(preKeyId));
  }

  private File getPreKeyDirectory() {
    File directory = new File(context.getFilesDir(), PREKEY_DIRECTORY);

    if (!directory.exists()) {
      if (!directory.mkdirs()) {
        Log.w(TAG, "PreKey directory creation failed!");
      }
    }

    return directory;
  }

  private byte[] readBlob(FileInputStream in) throws IOException {
    int length       = readInteger(in);
    byte[] blobBytes = new byte[length];

    in.read(blobBytes, 0, blobBytes.length);
    return blobBytes;
  }

  private void writeBlob(byte[] blobBytes, FileChannel out) throws IOException {
    writeInteger(blobBytes.length, out);
    out.write(ByteBuffer.wrap(blobBytes));
  }

  private int readInteger(FileInputStream in) throws IOException {
    byte[] integer = new byte[4];
    in.read(integer, 0, integer.length);
    return Conversions.byteArrayToInt(integer);
  }

  private void writeInteger(int value, FileChannel out) throws IOException {
    byte[] valueBytes = Conversions.intToByteArray(value);
    out.write(ByteBuffer.wrap(valueBytes));
  }

}
