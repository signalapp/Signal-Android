package org.thoughtcrime.securesms.database.keys;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.PreKeyPair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class PreKeyRecord extends Record {

  private static final Object FILE_LOCK              = new Object();
  private static final int    CURRENT_VERSION_MARKER = 1;

  private final MasterCipher masterCipher;
  private final MasterSecret masterSecret;

  private PreKeyPair keyPair;
  private long id;

  public PreKeyRecord(Context context, MasterSecret masterSecret, long id)
      throws InvalidKeyIdException
  {
    super(context, PREKEY_DIRECTORY, id+"");

    this.id           = id;
    this.masterSecret = masterSecret;
    this.masterCipher = new MasterCipher(masterSecret);

    loadData();
  }

  public PreKeyRecord(Context context, MasterSecret masterSecret,
                      long id, PreKeyPair keyPair)
  {
    super(context, PREKEY_DIRECTORY, id+"");
    this.id           = id;
    this.keyPair      = keyPair;
    this.masterSecret = masterSecret;
    this.masterCipher = new MasterCipher(masterSecret);
  }

  public long getId() {
    return id;
  }

  public PreKeyPair getKeyPair() {
    return keyPair;
  }

  public static boolean hasRecord(Context context, long id) {
    Log.w("PreKeyRecord", "Checking: " + id);
    return Record.hasRecord(context, PREKEY_DIRECTORY, id+"");
  }

  public static void delete(Context context, long id) {
    Record.delete(context, PREKEY_DIRECTORY, id+"");
  }

  public void save() {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);

        writeInteger(CURRENT_VERSION_MARKER, out);
        writeKeyPair(keyPair, out);

        out.force(true);
        out.truncate(out.position());
        out.close();
        file.close();
      } catch (IOException ioe) {
        Log.w("PreKeyRecord", ioe);
      }
    }
  }

  private void loadData() throws InvalidKeyIdException {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in = this.openInputStream();
        int recordVersion  = readInteger(in);

        if (recordVersion != CURRENT_VERSION_MARKER) {
          Log.w("PreKeyRecord", "Invalid version: " + recordVersion);
          return;
        }

        keyPair = readKeyPair(in, masterCipher);
        in.close();
      } catch (FileNotFoundException e) {
        Log.w("PreKeyRecord", e);
        throw new InvalidKeyIdException(e);
      } catch (IOException ioe) {
        Log.w("PreKeyRecord", ioe);
        throw new InvalidKeyIdException(ioe);
      } catch (InvalidKeyException ike) {
        Log.w("LocalKeyRecord", ike);
        throw new InvalidKeyIdException(ike);
      }
    }
  }

  private void writeKeyPair(PreKeyPair keyPair, FileChannel out) throws IOException {
    byte[] serialized = keyPair.serialize();
    writeBlob(serialized, out);
  }

  private PreKeyPair readKeyPair(FileInputStream in, MasterCipher masterCipher)
      throws IOException, InvalidKeyException
  {
    byte[] keyPairBytes = readBlob(in);
    return new PreKeyPair(masterSecret, keyPairBytes);
  }

}
