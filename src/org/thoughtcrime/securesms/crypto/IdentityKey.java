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

import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.thoughtcrime.securesms.util.Hex;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A class for representing an identity key.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKey implements Parcelable, SerializableKey {

  public static final Parcelable.Creator<IdentityKey> CREATOR = new Parcelable.Creator<IdentityKey>() {
    public IdentityKey createFromParcel(Parcel in) {
      try {
        return new IdentityKey(in);
      } catch (InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }

    public IdentityKey[] newArray(int size) {
      return new IdentityKey[size];
    }
  };
	
  public  static final int SIZE    = 1 + 33;
  private static final int VERSION = 1;
	
  private ECPublicKeyParameters publicKey;
	
  public IdentityKey(ECPublicKeyParameters publicKey) {
    this.publicKey = publicKey;
  }
	
  public IdentityKey(Parcel in) throws InvalidKeyException {
    int length        = in.readInt();
    byte[] serialized = new byte[length];
		
    in.readByteArray(serialized);		
    initializeFromSerialized(serialized, 0);
  }
	
  public IdentityKey(byte[] bytes, int offset) throws InvalidKeyException {
    initializeFromSerialized(bytes, offset);
  }
	
  public ECPublicKeyParameters getPublicKeyParameters() {
    return this.publicKey;
  }
	
  private void initializeFromSerialized(byte[] bytes, int offset) throws InvalidKeyException {
    int version       = bytes[offset] & 0xff;
		
    if (version > VERSION)
      throw new InvalidKeyException("Unsupported key version: " + version);
		
    byte[] pointBytes = new byte[PublicKey.POINT_SIZE];
    System.arraycopy(bytes, offset+1, pointBytes, 0, pointBytes.length);
		
    ECPoint Q;
		
    try {
      Q = KeyUtil.decodePoint(pointBytes);
    } catch (RuntimeException re) {
      throw new InvalidKeyException(re);
    }
		
    this.publicKey = new ECPublicKeyParameters(Q, KeyUtil.domainParameters);		
  }
	
  public byte[] serialize() {
    byte[] encodedKey = KeyUtil.encodePoint(publicKey.getQ());
    byte[] combined   = new byte[1 + encodedKey.length];
		
    combined[0]       = (byte)VERSION;
    System.arraycopy(encodedKey, 0, combined, 1, encodedKey.length);
		
    return combined;
  }
	
  public String getFingerprint() {
    return Hex.toString(serialize());
  }
	
  @Override
  public boolean equals(Object other) {
    if (!(other instanceof IdentityKey)) return false;
    return publicKey.getQ().equals(((IdentityKey)other).publicKey.getQ());
  }
	
  @Override
  public int hashCode() {
    return publicKey.getQ().hashCode();
  }

  public int describeContents() {
    // TODO Auto-generated method stub
    return 0;
  }

  public void writeToParcel(Parcel dest, int flags) {
    byte[] serialized = this.serialize();
    dest.writeInt(serialized.length);
    dest.writeByteArray(serialized);
  }
	
}
