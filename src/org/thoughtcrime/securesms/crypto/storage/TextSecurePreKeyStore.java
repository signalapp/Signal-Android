package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {

  public  static final String PREKEY_DIRECTORY        = "prekeys";
  public  static final String SIGNED_PREKEY_DIRECTORY = "signed_prekeys";


  private static final int    CURRENT_VERSION_MARKER = 1;
  private static final Object FILE_LOCK              = new Object();
  private static final String TAG                    = TextSecurePreKeyStore.class.getSimpleName();

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecurePreKeyStore(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      try {
        return new PreKeyRecord(loadSerializedRecord(getPreKeyFile(preKeyId)));
      } catch (IOException | InvalidMessageException e) {
        Log.w(TAG, e);
        throw new InvalidKeyIdException(e);
      }
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      try {
        return new SignedPreKeyRecord(loadSerializedRecord(getSignedPreKeyFile(signedPreKeyId)));
      } catch (IOException | InvalidMessageException e) {
        Log.w(TAG, e);
        throw new InvalidKeyIdException(e);
      }
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    synchronized (FILE_LOCK) {
      File                     directory = getSignedPreKeyDirectory();
      List<SignedPreKeyRecord> results   = new LinkedList<>();

      for (File signedPreKeyFile : directory.listFiles()) {
        try {
          results.add(new SignedPreKeyRecord(loadSerializedRecord(signedPreKeyFile)));
        } catch (IOException | InvalidMessageException e) {
          Log.w(TAG, e);
        }
      }

      return results;
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    synchronized (FILE_LOCK) {
      try {
        storeSerializedRecord(getPreKeyFile(preKeyId), record.serialize());
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    synchronized (FILE_LOCK) {
      try {
        storeSerializedRecord(getSignedPreKeyFile(signedPreKeyId), record.serialize());
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    File record = getPreKeyFile(preKeyId);
    return record.exists();
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    File record = getSignedPreKeyFile(signedPreKeyId);
    return record.exists();
  }


  @Override
  public void removePreKey(int preKeyId) {
    File record = getPreKeyFile(preKeyId);
    record.delete();
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    File record = getSignedPreKeyFile(signedPreKeyId);
    record.delete();
  }

  private byte[] loadSerializedRecord(File recordFile)
      throws IOException, InvalidMessageException
  {
    MasterCipher masterCipher  = new MasterCipher(masterSecret);
    FileInputStream fin           = new FileInputStream(recordFile);
    int             recordVersion = readInteger(fin);

    if (recordVersion != CURRENT_VERSION_MARKER) {
      throw new AssertionError("Invalid version: " + recordVersion);
    }

    return masterCipher.decryptBytes(readBlob(fin));
  }

  private void storeSerializedRecord(File file, byte[] serialized) throws IOException {
    MasterCipher     masterCipher = new MasterCipher(masterSecret);
    RandomAccessFile recordFile   = new RandomAccessFile(file, "rw");
    FileChannel      out          = recordFile.getChannel();

    out.position(0);
    writeInteger(CURRENT_VERSION_MARKER, out);
    writeBlob(masterCipher.encryptBytes(serialized), out);
    out.truncate(out.position());
    recordFile.close();
  }

  private File getPreKeyFile(int preKeyId) {
    return new File(getPreKeyDirectory(), String.valueOf(preKeyId));
  }

  private File getSignedPreKeyFile(int signedPreKeyId) {
    return new File(getSignedPreKeyDirectory(), String.valueOf(signedPreKeyId));
  }

  private File getPreKeyDirectory() {
    return getRecordsDirectory(PREKEY_DIRECTORY);
  }

  private File getSignedPreKeyDirectory() {
    return getRecordsDirectory(SIGNED_PREKEY_DIRECTORY);
  }

  private File getRecordsDirectory(String directoryName) {
    File directory = new File(context.getFilesDir(), directoryName);

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
