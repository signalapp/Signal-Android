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

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.ParcelUtil;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;

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

  public IdentityKeyParcelable(@Nullable IdentityKey identityKey) {
    this.identityKey = identityKey;
  }

  public IdentityKeyParcelable(Parcel in) throws InvalidKeyException {
    byte[] serialized = ParcelUtil.readByteArray(in);

    this.identityKey = serialized != null ? new IdentityKey(serialized, 0) : null;
  }

  public @Nullable IdentityKey get() {
    return identityKey;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    ParcelUtil.writeByteArray(dest, identityKey != null ? identityKey.serialize() : null);
  }
}
