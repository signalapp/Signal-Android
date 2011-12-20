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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.Hex;

import android.util.Log;

public class PublicKey {
  public  static final int POINT_SIZE = 33;
  public  static final int KEY_SIZE   = 3 + POINT_SIZE;
	
  private ECPublicKeyParameters publicKey;
  private int id;
	
  public PublicKey(PublicKey publicKey) {
    this.id        = publicKey.id;
		
    // FIXME :: This not strictly an accurate copy constructor.
    this.publicKey = publicKey.publicKey;
  }
	
  public PublicKey(int id, ECPublicKeyParameters publicKey) {
    this.publicKey = publicKey;
    this.id        = id;
  }

  public PublicKey(byte[] bytes, int offset) throws InvalidKeyException {
    Log.w("PublicKey", "PublicKey Length: " + (bytes.length - offset));
    if ((bytes.length - offset) < KEY_SIZE)
      throw new InvalidKeyException("Provided bytes are too short.");
			
    this.id           = Conversions.byteArrayToMedium(bytes, offset);
    byte[] pointBytes = new byte[POINT_SIZE];
		
    System.arraycopy(bytes, offset+3, pointBytes, 0, pointBytes.length);
		
    ECPoint Q;
		
    try {
      Q = KeyUtil.decodePoint(pointBytes);
    } catch (RuntimeException re) {
      throw new InvalidKeyException(re);
    }
		
    this.publicKey = new ECPublicKeyParameters(Q, KeyUtil.domainParameters);
  }
	
  public PublicKey(byte[] bytes) throws InvalidKeyException {
    this(bytes, 0);
  }
	
  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
	
  public ECPublicKeyParameters getKey() {
    return publicKey;
  }
	
  public String getFingerprint() {
    return Hex.toString(getFingerprintBytes());
  }
	
  public byte[] getFingerprintBytes() {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      return md.digest(serialize());
    } catch (NoSuchAlgorithmException nsae) {
      Log.w("LocalKeyPair", nsae);
      throw new IllegalArgumentException("SHA-1 isn't supported!");
    }
  }
	
  public byte[] serialize() {
    byte[] complete        = new byte[KEY_SIZE];
    byte[] serializedPoint = KeyUtil.encodePoint(publicKey.getQ());
				
    Log.w("PublicKey", "Serializing public key point: " + Hex.toString(serializedPoint));
		
    Conversions.mediumToByteArray(complete, 0, id);
    System.arraycopy(serializedPoint, 0, complete, 3, serializedPoint.length);
		
    return complete;
  }	
}
