/**
 * Copyright (C) 2011 Whisper Systems
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

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.KeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class LocalKeyRecord extends Record {

  private static final Object FILE_LOCK = new Object();

  private KeyPair localCurrentKeyPair;
  private KeyPair localNextKeyPair;

  private final MasterCipher masterCipher;
  private final MasterSecret masterSecret;

  public LocalKeyRecord(Context context, MasterSecret masterSecret, CanonicalRecipientAddress recipient) {
    super(context, SESSIONS_DIRECTORY, getFileNameForRecipient(context, recipient));
    this.masterSecret = masterSecret;
    this.masterCipher = new MasterCipher(masterSecret);
    loadData();
  }

  public static boolean hasRecord(Context context, CanonicalRecipientAddress recipient) {
    Log.w("LocalKeyRecord", "Checking: " + getFileNameForRecipient(context, recipient));
    return Record.hasRecord(context, SESSIONS_DIRECTORY, getFileNameForRecipient(context, recipient));
  }

  public static void delete(Context context, CanonicalRecipientAddress recipient) {
    Record.delete(context, SESSIONS_DIRECTORY, getFileNameForRecipient(context, recipient));
  }

  private static String getFileNameForRecipient(Context context, CanonicalRecipientAddress recipient) {
    return recipient.getCanonicalAddress(context) + "-local";
  }

  public void advanceKeyIfNecessary(int keyId) {
    Log.w("LocalKeyRecord", "Remote client acknowledges receiving key id: " + keyId);
    if (keyId == localNextKeyPair.getId()) {
      this.localCurrentKeyPair = this.localNextKeyPair;
      this.localNextKeyPair    = new KeyPair(this.localNextKeyPair.getId()+1, KeyUtil.generateKeyPair(), masterSecret);
    }
  }

  public void setCurrentKeyPair(KeyPair localCurrentKeyPair) {
    this.localCurrentKeyPair = localCurrentKeyPair;
  }

  public void setNextKeyPair(KeyPair localNextKeyPair) {
    this.localNextKeyPair = localNextKeyPair;
  }

  public KeyPair getCurrentKeyPair() {
    return this.localCurrentKeyPair;
  }

  public KeyPair getNextKeyPair() {
    return this.localNextKeyPair;
  }

  public KeyPair getKeyPairForId(int id) throws InvalidKeyIdException {
    if      (this.localCurrentKeyPair.getId() == id) return this.localCurrentKeyPair;
    else if (this.localNextKeyPair.getId() == id) return this.localNextKeyPair;
    else throw new InvalidKeyIdException("No local key for ID: " + id);
  }

  public void save() {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);

        writeKeyPair(localCurrentKeyPair, out);
        writeKeyPair(localNextKeyPair, out);

        out.force(true);
        out.truncate(out.position());
        out.close();
        file.close();
      } catch (IOException ioe) {
        Log.w("keyrecord", ioe);
        // XXX
      }
    }
  }

  private void loadData() {
    Log.w("LocalKeyRecord", "Loading local key record...");
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in  = this.openInputStream();
        localCurrentKeyPair = readKeyPair(in, masterCipher);
        localNextKeyPair    = readKeyPair(in, masterCipher);
        in.close();
      } catch (FileNotFoundException e) {
        Log.w("LocalKeyRecord", "No local keypair set found.");
      } catch (IOException ioe) {
        Log.w("keyrecord", ioe);
        // XXX
      } catch (InvalidKeyException ike) {
        Log.w("LocalKeyRecord", ike);
      }
    }
  }

  private void writeKeyPair(KeyPair keyPair, FileChannel out) throws IOException {
    byte[] keyPairBytes = keyPair.toBytes();
    writeBlob(keyPairBytes, out);
  }

  private KeyPair readKeyPair(FileInputStream in, MasterCipher masterCipher)
      throws IOException, InvalidKeyException
  {
    byte[] keyPairBytes = readBlob(in);
    return new KeyPair(keyPairBytes, masterCipher);
  }

}
