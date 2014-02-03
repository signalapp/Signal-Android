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
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Medium;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Represents the current and last public key belonging to the "remote"
 * endpoint in an encrypted session.  These are stored on disk.
 *
 * @author Moxie Marlinspike
 */

public class RemoteKeyRecord extends Record {
  private static final Object FILE_LOCK = new Object();

  private PublicKey remoteKeyCurrent;
  private PublicKey remoteKeyLast;

  public RemoteKeyRecord(Context context, CanonicalRecipient recipient) {
    super(context, SESSIONS_DIRECTORY, getFileNameForRecipient(recipient));
    loadData();
  }

  public static void delete(Context context, CanonicalRecipient recipient) {
    delete(context, SESSIONS_DIRECTORY, getFileNameForRecipient(recipient));
  }

  public static boolean hasRecord(Context context, CanonicalRecipient recipient) {
    Log.w("LocalKeyRecord", "Checking: " + getFileNameForRecipient(recipient));
    return hasRecord(context, SESSIONS_DIRECTORY, getFileNameForRecipient(recipient));
  }

  private static String getFileNameForRecipient(CanonicalRecipient recipient) {
    return recipient.getRecipientId() + "-remote";
  }

  public void updateCurrentRemoteKey(PublicKey remoteKey) {
    Log.w("RemoteKeyRecord", "Updating current remote key: " + remoteKey.getId());
    if (isWrappingGreaterThan(remoteKey.getId(), remoteKeyCurrent.getId())) {
      this.remoteKeyLast    = this.remoteKeyCurrent;
      this.remoteKeyCurrent = remoteKey;
    }
  }

  public void setCurrentRemoteKey(PublicKey remoteKeyCurrent) {
    this.remoteKeyCurrent = remoteKeyCurrent;
  }

  public void setLastRemoteKey(PublicKey remoteKeyLast) {
    this.remoteKeyLast = remoteKeyLast;
  }

  public PublicKey getCurrentRemoteKey() {
    return this.remoteKeyCurrent;
  }

  public PublicKey getLastRemoteKey() {
    return this.remoteKeyLast;
  }

  public PublicKey getKeyForId(int id) throws InvalidKeyIdException {
    if (this.remoteKeyCurrent.getId() == id) return this.remoteKeyCurrent;
    else if (this.remoteKeyLast.getId() == id) return this.remoteKeyLast;
    else throw new InvalidKeyIdException("No remote key for ID: " + id);
  }

  public void save() {
    Log.w("RemoteKeyRecord", "Saving remote key record for recipient: " + this.address);
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        Log.w("RemoteKeyRecord", "Opened file of size: " + out.size());
        out.position(0);

        writeKey(remoteKeyCurrent, out);
        writeKey(remoteKeyLast, out);

        out.truncate(out.position());
        out.close();
        file.close();
      } catch (IOException ioe) {
        Log.w("keyrecord", ioe);
        // XXX
      }
    }
  }

  private boolean isWrappingGreaterThan(int receivedValue, int currentValue) {
    if (receivedValue > currentValue) {
      return true;
    }

    if (receivedValue == currentValue) {
      return false;
    }

    int gap = (receivedValue - currentValue) + Medium.MAX_VALUE;

    return (gap >= 0) && (gap < 5);
  }

  private void loadData() {
    Log.w("RemoteKeyRecord", "Loading remote key record for recipient: " + this.address);
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in  = this.openInputStream();
        remoteKeyCurrent    = readKey(in);
        remoteKeyLast       = readKey(in);
        in.close();
      } catch (FileNotFoundException e) {
        Log.w("RemoteKeyRecord", "No remote keys found.");
      } catch (IOException ioe) {
        Log.w("keyrecord", ioe);
        // XXX
      }
    }
  }

  private void writeKey(PublicKey key, FileChannel out) throws IOException {
    byte[] keyBytes = key.serialize();
    Log.w("RemoteKeyRecord", "Serializing remote key bytes: " + Hex.toString(keyBytes));
    writeBlob(keyBytes, out);
  }

  private PublicKey readKey(FileInputStream in) throws IOException {
    try {
      byte[] keyBytes = readBlob(in);
      return new PublicKey(keyBytes);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    }
  }

}
