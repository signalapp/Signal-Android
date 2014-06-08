/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.whispersystems.textsecure.push;

import android.os.Parcel;
import android.os.Parcelable;

public class PushAttachmentPointer implements Parcelable {

  public static final Parcelable.Creator<PushAttachmentPointer> CREATOR = new Parcelable.Creator<PushAttachmentPointer>() {
    @Override
    public PushAttachmentPointer createFromParcel(Parcel in) {
      return new PushAttachmentPointer(in);
    }

    @Override
    public PushAttachmentPointer[] newArray(int size) {
      return new PushAttachmentPointer[size];
    }
  };

  private final String contentType;
  private final long   id;
  private final byte[] key;

  public PushAttachmentPointer(String contentType, long id, byte[] key) {
    this.contentType = contentType;
    this.id          = id;
    this.key         = key;
  }

  public PushAttachmentPointer(Parcel in) {
    this.contentType = in.readString();
    this.id          = in.readLong();

    int keyLength = in.readInt();
    this.key      = new byte[keyLength];
    in.readByteArray(this.key);
  }

  public String getContentType() {
    return contentType;
  }

  public long getId() {
    return id;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(contentType);
    dest.writeLong(id);
    dest.writeInt(this.key.length);
    dest.writeByteArray(this.key);
  }
}
