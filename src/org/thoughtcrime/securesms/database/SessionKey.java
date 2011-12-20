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

import javax.crypto.spec.SecretKeySpec;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.Util;

/**
 * Represents the currently negotiated session key for a given
 * local key id and remote key id.  This is stored encrypted on
 * disk.
 * 
 * @author Moxie Marlinspike
 */

public class SessionKey {

  private int localKeyId;
  private int remoteKeyId;
  private SecretKeySpec cipherKey;
  private SecretKeySpec macKey;
  private MasterCipher masterCipher;
	
  public SessionKey(int localKeyId, int remoteKeyId, SecretKeySpec cipherKey, SecretKeySpec macKey, MasterSecret masterSecret) {
    this.localKeyId   = localKeyId;
    this.remoteKeyId  = remoteKeyId;
    this.cipherKey    = cipherKey;
    this.macKey       = macKey;
    this.masterCipher = new MasterCipher(masterSecret);
  }
	
  public SessionKey(byte[] bytes, MasterSecret masterSecret) {
    this.masterCipher = new MasterCipher(masterSecret);
    deserialize(bytes);
  }
	
  public byte[] serialize() {
    byte[] localKeyIdBytes  = Conversions.mediumToByteArray(localKeyId);
    byte[] remoteKeyIdBytes = Conversions.mediumToByteArray(remoteKeyId);
    byte[] cipherKeyBytes   = cipherKey.getEncoded();
    byte[] macKeyBytes      = macKey.getEncoded();
    byte[] combined         = Util.combine(localKeyIdBytes, remoteKeyIdBytes, cipherKeyBytes, macKeyBytes);
		
    return masterCipher.encryptBytes(combined);
  }
	
  private void deserialize(byte[] bytes) {
    byte[] decrypted = masterCipher.encryptBytes(bytes);
    this.localKeyId  = Conversions.byteArrayToMedium(decrypted, 0);
    this.remoteKeyId = Conversions.byteArrayToMedium(decrypted, 3);
		
    byte[] keyBytes  = new byte[SessionCipher.CIPHER_KEY_LENGTH];
    System.arraycopy(decrypted, 6, keyBytes, 0, keyBytes.length);

    byte[] macBytes  = new byte[SessionCipher.MAC_KEY_LENGTH];
    System.arraycopy(decrypted, 6 + keyBytes.length, macBytes, 0, macBytes.length);
		
    this.cipherKey         = new SecretKeySpec(keyBytes, "AES");
    this.macKey            = new SecretKeySpec(macBytes, "HmacSHA1");
  }

  public int getLocalKeyId() {
    return this.localKeyId;
  }

  public int getRemoteKeyId() {
    return this.remoteKeyId;
  }

  public SecretKeySpec getCipherKey() {
    return this.cipherKey;
  }

  public SecretKeySpec getMacKey() {
    return this.macKey;
  }
	
}
