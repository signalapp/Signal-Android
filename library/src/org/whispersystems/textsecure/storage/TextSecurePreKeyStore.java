package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TextSecurePreKeyStore implements PreKeyStore {

  public  static final String PREKEY_DIRECTORY = "prekeys";
  private static final String TAG              = TextSecurePreKeyStore.class.getSimpleName();

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecurePreKeyStore(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public PreKeyRecord load(int preKeyId) throws InvalidKeyIdException {
    try {
      FileInputStream fin = new FileInputStream(getPreKeyFile(preKeyId));
      return new TextSecurePreKeyRecord(masterSecret, fin);
    } catch (IOException | InvalidMessageException e) {
      Log.w(TAG, e);
      throw new InvalidKeyIdException(e);
    }
  }

  @Override
  public void store(int preKeyId, PreKeyRecord record) {
    try {
      RandomAccessFile recordFile = new RandomAccessFile(getPreKeyFile(preKeyId), "rw");
      FileChannel      out        = recordFile.getChannel();

      out.position(0);
      out.write(ByteBuffer.wrap(record.serialize()));
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
}
