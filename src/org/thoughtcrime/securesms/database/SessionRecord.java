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

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;

import android.content.Context;
import android.util.Log;

/**
 * A disk record representing a current session.
 * 
 * @author Moxie Marlinspike
 */

public class SessionRecord extends Record {
  private static final int CURRENT_VERSION_MARKER  = 0X55555556;
  private static final int[] VALID_VERSION_MARKERS = {CURRENT_VERSION_MARKER, 0X55555555};
  private static final Object FILE_LOCK            = new Object();
	
  private int counter;
  private byte[] localFingerprint;
  private byte[] remoteFingerprint;
  private int sessionVersion;
	
  private IdentityKey identityKey;
  private SessionKey sessionKeyRecord;
  private boolean verifiedSessionKey;
	
  private final MasterSecret masterSecret;
	
  public SessionRecord(Context context, MasterSecret masterSecret, Recipient recipient) {
    super(context, getFileNameForRecipient(context, recipient));
    this.masterSecret   = masterSecret;
    this.sessionVersion = 31337;
    loadData();
  }
	
  public static void delete(Context context, Recipient recipient) {
    Record.delete(context, getFileNameForRecipient(context, recipient));
  }
	
  public static boolean hasSession(Context context, Recipient recipient) {
    Log.w("LocalKeyRecord", "Checking: " + getFileNameForRecipient(context, recipient));
    return Record.hasRecord(context, getFileNameForRecipient(context, recipient));
  }

  private static String getFileNameForRecipient(Context context, Recipient recipient) {
    return CanonicalAddressDatabase.getInstance(context).getCanonicalAddress(recipient.getNumber()) + "";
  }
	
  public void setSessionKey(SessionKey sessionKeyRecord) {
    this.sessionKeyRecord = sessionKeyRecord;
  }
	
  public void setSessionId(byte[] localFingerprint, byte[] remoteFingerprint) {
    this.localFingerprint  = localFingerprint;
    this.remoteFingerprint = remoteFingerprint;
  }
	
  public void setIdentityKey(IdentityKey identityKey) {
    this.identityKey = identityKey;
  }
	
  public int getSessionVersion() {
    return (sessionVersion == 31337 ? 0 : sessionVersion);
  }
	
  public void setSessionVersion(int sessionVersion) {
    this.sessionVersion = sessionVersion;
  }
	
  public int getCounter() {
    return this.counter;
  }
	
  public void incrementCounter() {
    this.counter++;
  }
	
  public byte[] getLocalFingerprint() {
    return this.localFingerprint;
  }
	
  public byte[] getRemoteFingerprint() {
    return this.remoteFingerprint;
  }
	
  public IdentityKey getIdentityKey() {
    return this.identityKey;
  }
	
  public void setVerifiedSessionKey(boolean verifiedSessionKey) {
    this.verifiedSessionKey = verifiedSessionKey;
  }
	
  public boolean isVerifiedSession() {
    return this.verifiedSessionKey;
  }
	
  private void writeIdentityKey(FileChannel out) throws IOException {
    if (identityKey == null) writeBlob(new byte[0], out);
    else                     writeBlob(identityKey.serialize(), out);
  }
	
  private boolean isValidVersionMarker(int versionMarker) {
    for (int i=0;i<VALID_VERSION_MARKERS.length;i++) 
      if (versionMarker == VALID_VERSION_MARKERS[i]) 
	return true;
		
    return false;
  }
	
  private void readIdentityKey(FileInputStream in) throws IOException {
    try {
      byte[] blob = readBlob(in);
			
      if (blob.length == 0) this.identityKey = null;
      else                  this.identityKey = new IdentityKey(blob, 0);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    }
  }
	
  public void save() {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);
	
        writeInteger(CURRENT_VERSION_MARKER, out);
        writeInteger(counter, out);
        writeBlob(localFingerprint, out);
        writeBlob(remoteFingerprint, out);
        writeInteger(sessionVersion, out);
        writeIdentityKey(out);
        writeInteger(verifiedSessionKey ? 1 : 0, out);
				
        if (sessionKeyRecord != null)
          writeBlob(sessionKeyRecord.serialize(), out);
				
        out.truncate(out.position());
        file.close();
      } catch (IOException ioe) {
        throw new IllegalArgumentException(ioe);
      }
    }
  }

  private void loadData() {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in = this.openInputStream();
        int versionMarker  = readInteger(in);
				
        // Sigh, always put a version number on everything.
        if (!isValidVersionMarker(versionMarker)) {
          this.counter           = versionMarker;
          this.localFingerprint  = readBlob(in);
          this.remoteFingerprint = readBlob(in);
          this.sessionVersion    = 31337;					
					
          if (in.available() != 0)
            this.sessionKeyRecord = new SessionKey(readBlob(in), masterSecret);
				
          in.close();
        } else {
          this.counter           = readInteger(in);
          this.localFingerprint  = readBlob(in);
          this.remoteFingerprint = readBlob(in);
          this.sessionVersion    = readInteger(in);
					
          if (versionMarker >=  0X55555556) { 
            readIdentityKey(in);
            this.verifiedSessionKey = (readInteger(in) == 1) ? true : false;
          }
					
          if (in.available() != 0)
            this.sessionKeyRecord = new SessionKey(readBlob(in), masterSecret);
					
          in.close();
        }
      } catch (FileNotFoundException e) {
        Log.w("SessionRecord", "No session information found.");
        return;
      } catch (IOException ioe) {
        	Log.w("keyrecord", ioe);
        	// XXX
      }
    }
  }

  public SessionKey getSessionKey(int localKeyId, int remoteKeyId) {
    if (this.sessionKeyRecord == null) return null;
		
    if ((this.sessionKeyRecord.getLocalKeyId() == localKeyId) &&
        (this.sessionKeyRecord.getRemoteKeyId() == remoteKeyId))
        return this.sessionKeyRecord;

    return null;
  }	
	
}
