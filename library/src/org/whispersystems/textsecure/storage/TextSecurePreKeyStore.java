package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.DeviceKeyRecord;
import org.whispersystems.libaxolotl.state.DeviceKeyStore;
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
import java.util.LinkedList;
import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, DeviceKeyStore {

  public  static final String PREKEY_DIRECTORY     = "prekeys";
  public  static final String DEVICE_KEY_DIRECTORY = "device_keys";


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
  public DeviceKeyRecord loadDeviceKey(int deviceKeyId) throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      try {
        return new DeviceKeyRecord(loadSerializedRecord(getDeviceKeyFile(deviceKeyId)));
      } catch (IOException | InvalidMessageException e) {
        Log.w(TAG, e);
        throw new InvalidKeyIdException(e);
      }
    }
  }

  @Override
  public List<DeviceKeyRecord> loadDeviceKeys() {
    synchronized (FILE_LOCK) {
      File                  directory = getDeviceKeyDirectory();
      List<DeviceKeyRecord> results   = new LinkedList<>();

      for (File deviceKeyFile : directory.listFiles()) {
        try {
          results.add(new DeviceKeyRecord(loadSerializedRecord(deviceKeyFile)));
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
  public void storeDeviceKey(int deviceKeyId, DeviceKeyRecord record) {
    synchronized (FILE_LOCK) {
      try {
        storeSerializedRecord(getDeviceKeyFile(deviceKeyId), record.serialize());
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
  public boolean containsDeviceKey(int deviceKeyId) {
    File record = getDeviceKeyFile(deviceKeyId);
    return record.exists();
  }


  @Override
  public void removePreKey(int preKeyId) {
    File record = getPreKeyFile(preKeyId);
    record.delete();
  }

  @Override
  public void removeDeviceKey(int deviceKeyId) {
    File record = getDeviceKeyFile(deviceKeyId);
    record.delete();
  }

  private byte[] loadSerializedRecord(File recordFile)
      throws IOException, InvalidMessageException
  {
    MasterCipher    masterCipher  = new MasterCipher(masterSecret);
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

  private File getDeviceKeyFile(int deviceKeyId) {
    return new File(getDeviceKeyDirectory(), String.valueOf(deviceKeyId));
  }

  private File getPreKeyDirectory() {
    return getRecordsDirectory(PREKEY_DIRECTORY);
  }

  private File getDeviceKeyDirectory() {
    return getRecordsDirectory(DEVICE_KEY_DIRECTORY);
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
