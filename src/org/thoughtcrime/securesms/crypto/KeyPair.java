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
package org.thoughtcrime.securesms.crypto;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.thoughtcrime.securesms.util.Hex;

import android.util.Log;

/**
 * Represents a session's active KeyPair.
 * 
 * @author Moxie Marlinspike
 */

public class KeyPair {

  private ECPrivateKeyParameters privateKey;
  private PublicKey publicKey;
		
  private final MasterCipher masterCipher;
	
  public KeyPair(int keyPairId, AsymmetricCipherKeyPair keyPair, MasterSecret masterSecret) {
    this.masterCipher = new MasterCipher(masterSecret);
    this.publicKey    = new PublicKey(keyPairId, (ECPublicKeyParameters)keyPair.getPublic());
    this.privateKey   = (ECPrivateKeyParameters)keyPair.getPrivate();
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
	
  public AsymmetricCipherKeyPair getKeyPair() {
    return new AsymmetricCipherKeyPair(publicKey.getKey(), privateKey);
  }
	
  public byte[] toBytes() {
    return serialize();
  }
	
  private void deserialize(byte[] bytes) throws InvalidKeyException {
    this.publicKey         = new PublicKey(bytes);
    byte[] privateKeyBytes = new byte[bytes.length - PublicKey.KEY_SIZE];
    System.arraycopy(bytes, PublicKey.KEY_SIZE, privateKeyBytes, 0, privateKeyBytes.length);
    this.privateKey        = masterCipher.decryptKey(privateKeyBytes);
  }
	
  public byte[] serialize() {
    byte[] publicKeyBytes  = publicKey.serialize();
    Log.w("KeyPair", "Serialized public key bytes: " + Hex.toString(publicKeyBytes));
    byte[] privateKeyBytes = masterCipher.encryptKey(privateKey);		
    byte[] combined        = new byte[publicKeyBytes.length + privateKeyBytes.length];
    System.arraycopy(publicKeyBytes, 0, combined, 0, publicKeyBytes.length);
    System.arraycopy(privateKeyBytes, 0, combined, publicKeyBytes.length, privateKeyBytes.length);

    return combined;
  }
	
}
