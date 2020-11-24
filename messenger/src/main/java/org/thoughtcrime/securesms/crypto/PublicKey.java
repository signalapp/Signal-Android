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
package org.thoughtcrime.securesms.crypto;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.thoughtcrime.securesms.util.Conversions;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PublicKey {

  private static final String TAG = PublicKey.class.getSimpleName();

  public static final int KEY_SIZE = 3 + ECPublicKey.KEY_SIZE;

  private final ECPublicKey publicKey;
  private int id;
	
  public PublicKey(PublicKey publicKey) {
    this.id        = publicKey.id;
		
    // FIXME :: This not strictly an accurate copy constructor.
    this.publicKey = publicKey.publicKey;
  }
	
  public PublicKey(int id, ECPublicKey publicKey) {
    this.publicKey = publicKey;
    this.id        = id;
  }

  public PublicKey(byte[] bytes, int offset) throws InvalidKeyException {
    Log.i(TAG, "PublicKey Length: " + (bytes.length - offset));

    if ((bytes.length - offset) < KEY_SIZE)
      throw new InvalidKeyException("Provided bytes are too short.");

    this.id        = Conversions.byteArrayToMedium(bytes, offset);
    this.publicKey = Curve.decodePoint(bytes, offset + 3);
  }

  public PublicKey(byte[] bytes) throws InvalidKeyException {
    this(bytes, 0);
  }

  public int getType() {
    return publicKey.getType();
  }
	
  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
	
  public ECPublicKey getKey() {
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
    byte[] keyIdBytes      = Conversions.mediumToByteArray(id);
    byte[] serializedPoint = publicKey.serialize();

    Log.i(TAG, "Serializing public key point: " + Hex.toString(serializedPoint));

    return Util.combine(keyIdBytes, serializedPoint);
  }
}
