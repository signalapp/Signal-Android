/**
 * Copyright (C) 2011-2014 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class PreKeyRecord extends Record {

  private static final Object FILE_LOCK              = new Object();
  private static final int    CURRENT_VERSION_MARKER = 1;

  private final MasterSecret masterSecret;
  private StorageProtos.PreKeyRecordStructure structure;

  public PreKeyRecord(Context context, MasterSecret masterSecret, int id)
      throws InvalidKeyIdException
  {
    super(context, PREKEY_DIRECTORY, id+"");

    this.structure    = StorageProtos.PreKeyRecordStructure.newBuilder().setId(id).build();
    this.masterSecret = masterSecret;

    loadData();
  }

  public PreKeyRecord(Context context, MasterSecret masterSecret,
                      int id, ECKeyPair keyPair)
  {
    super(context, PREKEY_DIRECTORY, id+"");
    this.masterSecret = masterSecret;
    this.structure    = StorageProtos.PreKeyRecordStructure.newBuilder()
                                     .setId(id)
                                     .setPublicKey(ByteString.copyFrom(keyPair.getPublicKey()
                                                                              .serialize()))
                                     .setPrivateKey(ByteString.copyFrom(keyPair.getPrivateKey()
                                                                               .serialize()))
                                     .build();
  }

  public int getId() {
    return this.structure.getId();
  }

  public ECKeyPair getKeyPair() {
    try {
      ECPublicKey  publicKey  = Curve.decodePoint(this.structure.getPublicKey().toByteArray(), 0);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(this.structure.getPrivateKey().toByteArray());

      return new ECKeyPair(publicKey, privateKey);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
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

        MasterCipher masterCipher = new MasterCipher(masterSecret);

        writeInteger(CURRENT_VERSION_MARKER, out);
        writeBlob(masterCipher.encryptBytes(structure.toByteArray()), out);

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
        MasterCipher    masterCipher  = new MasterCipher(masterSecret);
        FileInputStream in            = this.openInputStream();
        int             recordVersion = readInteger(in);

        if (recordVersion != CURRENT_VERSION_MARKER) {
          Log.w("PreKeyRecord", "Invalid version: " + recordVersion);
          return;
        }

        this.structure =
            StorageProtos.PreKeyRecordStructure.parseFrom(masterCipher.decryptBytes(readBlob(in)));

        in.close();
      } catch (FileNotFoundException e) {
        Log.w("PreKeyRecord", e);
        throw new InvalidKeyIdException(e);
      } catch (IOException ioe) {
        Log.w("PreKeyRecord", ioe);
        throw new InvalidKeyIdException(ioe);
      } catch (InvalidMessageException ime) {
        Log.w("PreKeyRecord", ime);
        throw new InvalidKeyIdException(ime);
      }
    }
  }
}
