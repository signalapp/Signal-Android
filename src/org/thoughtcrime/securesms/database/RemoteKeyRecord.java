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
package org.thoughtcrime.securesms.database;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.PublicKey;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Hex;

import android.content.Context;
import android.util.Log;

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
	
  public RemoteKeyRecord(Context context, Recipient recipient) {
    super(context,getFileNameForRecipient(context, recipient));
    loadData();
  }
	
  public static void delete(Context context, Recipient recipient) {
    Record.delete(context, getFileNameForRecipient(context, recipient));
  }
	
  public static boolean hasRecord(Context context, Recipient recipient) {
    Log.w("LocalKeyRecord", "Checking: " + getFileNameForRecipient(context, recipient));
    return Record.hasRecord(context, getFileNameForRecipient(context, recipient));
  }
	
  private static String getFileNameForRecipient(Context context, Recipient recipient) {
    return CanonicalAddressDatabase.getInstance(context).getCanonicalAddress(recipient.getNumber()) + "-remote";
  }
	
  public void updateCurrentRemoteKey(PublicKey remoteKey) {
    Log.w("RemoteKeyRecord", "Updating current remote key: " + remoteKey.getId());
    if (remoteKey.getId() > remoteKeyCurrent.getId()) {
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
        return;
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
