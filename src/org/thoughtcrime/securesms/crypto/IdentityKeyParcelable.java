/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.os.Parcel;
import android.os.Parcelable;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;

public class IdentityKeyParcelable implements Parcelable {

  public static final Parcelable.Creator<IdentityKeyParcelable> CREATOR = new Parcelable.Creator<IdentityKeyParcelable>() {
    public IdentityKeyParcelable createFromParcel(Parcel in) {
      try {
        return new IdentityKeyParcelable(in);
      } catch (InvalidKeyException e) {
        throw new AssertionError(e);
      }
    }

    public IdentityKeyParcelable[] newArray(int size) {
      return new IdentityKeyParcelable[size];
    }
  };

  private final IdentityKey identityKey;

  public IdentityKeyParcelable(IdentityKey identityKey) {
    this.identityKey = identityKey;
  }

  public IdentityKeyParcelable(Parcel in) throws InvalidKeyException {
    int    serializedLength = in.readInt();
    byte[] serialized       = new byte[serializedLength];

    in.readByteArray(serialized);
    this.identityKey = new IdentityKey(serialized, 0);
  }

  public IdentityKey get() {
    return identityKey;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(identityKey.serialize().length);
    dest.writeByteArray(identityKey.serialize());
  }
}
