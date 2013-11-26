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

import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.kdf.NKDF;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import javax.crypto.spec.SecretKeySpec;

/**
 * Represents the currently negotiated session key for a given
 * local key id and remote key id.  This is stored encrypted on
 * disk.
 *
 * @author Moxie Marlinspike
 */

public class SessionKey {

  private int           mode;
  private int           localKeyId;
  private int           remoteKeyId;
  private SecretKeySpec cipherKey;
  private SecretKeySpec macKey;
  private MasterCipher  masterCipher;

  public SessionKey(int mode, int localKeyId, int remoteKeyId,
                    SecretKeySpec cipherKey, SecretKeySpec macKey,
                    MasterSecret masterSecret)
  {
    this.mode         = mode;
    this.localKeyId   = localKeyId;
    this.remoteKeyId  = remoteKeyId;
    this.cipherKey    = cipherKey;
    this.macKey       = macKey;
    this.masterCipher = new MasterCipher(masterSecret);
  }

  public SessionKey(byte[] bytes, MasterSecret masterSecret) throws InvalidMessageException {
    this.masterCipher = new MasterCipher(masterSecret);
    deserialize(bytes);
  }

  public byte[] serialize() {
    byte[] localKeyIdBytes  = Conversions.mediumToByteArray(localKeyId);
    byte[] remoteKeyIdBytes = Conversions.mediumToByteArray(remoteKeyId);
    byte[] cipherKeyBytes   = cipherKey.getEncoded();
    byte[] macKeyBytes      = macKey.getEncoded();
    byte[] modeBytes        = {(byte)mode};
    byte[] combined         = Util.combine(localKeyIdBytes, remoteKeyIdBytes,
                                           cipherKeyBytes, macKeyBytes, modeBytes);

    return masterCipher.encryptBytes(combined);
  }

  private void deserialize(byte[] bytes) throws InvalidMessageException {
    byte[] decrypted = masterCipher.decryptBytes(bytes);
    
    this.localKeyId  = Conversions.byteArrayToMedium(decrypted, 0);
    this.remoteKeyId = Conversions.byteArrayToMedium(decrypted, 3);

    byte[] keyBytes  = new byte[NKDF.LEGACY_CIPHER_KEY_LENGTH];
    System.arraycopy(decrypted, 6, keyBytes, 0, keyBytes.length);

    byte[] macBytes  = new byte[NKDF.LEGACY_MAC_KEY_LENGTH];
    System.arraycopy(decrypted, 6 + keyBytes.length, macBytes, 0, macBytes.length);
    
    if (decrypted.length < 6 + NKDF.LEGACY_CIPHER_KEY_LENGTH + NKDF.LEGACY_MAC_KEY_LENGTH + 1) {
      throw new InvalidMessageException("No mode included");
    }
    
    this.mode = decrypted[6 + keyBytes.length + macBytes.length];

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

  public int getMode() {
    return mode;
  }
}
