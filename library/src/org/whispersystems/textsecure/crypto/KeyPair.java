/** 
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
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
package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Util;

/**
 * Represents a session's active KeyPair.
 * 
 * @author Moxie Marlinspike
 */

public class KeyPair {

  private PublicKey    publicKey;
  private ECPrivateKey privateKey;

  private final MasterCipher masterCipher;
	
  public KeyPair(int keyPairId, ECKeyPair keyPair, MasterSecret masterSecret) {
    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PublicKey(keyPairId, keyPair.getPublicKey());
    this.privateKey   = keyPair.getPrivateKey();
  }
	
  public KeyPair(byte[] bytes, MasterCipher masterCipher) throws InvalidKeyException {
    this.masterCipher = masterCipher;
    deserialize(bytes);
  }
	
  public int getId() {
    return publicKey.getId();
  }
	
  public PublicKey getPublicKey() {
    return publicKey;
  }

  public ECPrivateKey getPrivateKey() {
    return privateKey;
  }

  public byte[] toBytes() {
    return serialize();
  }
	
  private void deserialize(byte[] bytes) throws InvalidKeyException {
    this.publicKey         = new PublicKey(bytes);
    byte[] privateKeyBytes = new byte[bytes.length - PublicKey.KEY_SIZE];
    System.arraycopy(bytes, PublicKey.KEY_SIZE, privateKeyBytes, 0, privateKeyBytes.length);
    this.privateKey        = masterCipher.decryptKey(this.publicKey.getType(), privateKeyBytes);
  }
	
  public byte[] serialize() {
    byte[] publicKeyBytes  = publicKey.serialize();
    Log.w("KeyPair", "Serialized public key bytes: " + Hex.toString(publicKeyBytes));
    byte[] privateKeyBytes = masterCipher.encryptKey(privateKey);
    return Util.combine(publicKeyBytes, privateKeyBytes);
  }
	
}
