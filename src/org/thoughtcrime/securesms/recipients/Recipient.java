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
package org.thoughtcrime.securesms.recipients;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class Recipient implements Parcelable {

  public static final Parcelable.Creator<Recipient> CREATOR = new Parcelable.Creator<Recipient>() {
    public Recipient createFromParcel(Parcel in) {
      return new Recipient(in);
    }

    public Recipient[] newArray(int size) {
      return new Recipient[size];
    }
  };

  private final String name;
  private final String number;
  private Uri contactUri;
  private Bitmap contactPhoto;

  public Recipient(String name, String number, Uri contactUri, Bitmap contactPhoto) {
    this(name, number, contactPhoto);
    this.contactUri = contactUri;
  }

  public Recipient(String name, String number, Bitmap contactPhoto) {
    this.name         = name;
    this.number       = number;
    this.contactPhoto = contactPhoto;
  }

  public Recipient(Parcel in) {
    this.name       = in.readString();
    this.number     = in.readString();
    this.contactUri = in.readParcelable(null);
  }

  public Uri getContactUri() {
    return this.contactUri;
  }

  public String getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(name);
    dest.writeString(number);
    dest.writeParcelable(contactUri, 0);
  }

  public String toShortString() {
    return (name == null ? number : name);
  }

  public Bitmap getContactPhoto() {
    return contactPhoto;
  }


}
