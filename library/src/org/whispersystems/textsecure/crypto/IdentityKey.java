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

import android.os.Parcel;
import android.os.Parcelable;

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Hex;

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
	
  public  static final int NIST_SIZE = 1 + ECPublicKey.KEY_SIZE;

  private ECPublicKey publicKey;

  public IdentityKey(ECPublicKey publicKey) {
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

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  private void initializeFromSerialized(byte[] bytes, int offset) throws InvalidKeyException {
    if ((bytes[offset] & 0xff) == 1) {
      this.publicKey = Curve.decodePoint(bytes, offset +1);
    } else {
      this.publicKey = Curve.decodePoint(bytes, offset);
    }
  }
	
  public byte[] serialize() {
    return publicKey.serialize();
  }

  public String getFingerprint() {
    return Hex.toString(publicKey.serialize());
  }
	
  @Override
  public boolean equals(Object other) {
    if (other == null)                   return false;
    if (!(other instanceof IdentityKey)) return false;

    return publicKey.equals(((IdentityKey) other).getPublicKey());
  }
	
  @Override
  public int hashCode() {
    return publicKey.hashCode();
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
