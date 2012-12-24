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

import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;

import java.util.concurrent.atomic.AtomicReference;

public class Recipient implements Parcelable {

  public static final Parcelable.Creator<Recipient> CREATOR = new Parcelable.Creator<Recipient>() {
    public Recipient createFromParcel(Parcel in) {
      return new Recipient(in);
    }

    public Recipient[] newArray(int size) {
      return new Recipient[size];
    }
  };

  private final AtomicReference<String> name         = new AtomicReference<String>(null);
  private final AtomicReference<Bitmap> contactPhoto = new AtomicReference<Bitmap>(null);
  private final AtomicReference<Uri>    contactUri   = new AtomicReference<Uri>(null);

  private final String number;

  private RecipientModifiedListener listener;
  private boolean asynchronousUpdateComplete = false;

//  public Recipient(String name, String number, Uri contactUri, Bitmap contactPhoto) {
//    this(name, number, contactPhoto);
//    this.contactUri = contactUri;
//  }

//  public Recipient(String number, Bitmap contactPhoto,
//                   ListenableFutureTask<RecipientDetails> future)
//  {
//    this.number       = number;
//    this.contactUri   = null;
//    this.contactPhoto.set(contactPhoto);
//
//    future.setListener(new FutureTaskListener<RecipientDetails>() {
//      @Override
//      public void onSuccess(RecipientDetails result) {
//        if (result != null) {
//          Recipient.this.name.set(result.name);
//          Recipient.this.contactPhoto.set(result.avatar);
//
//          synchronized(this) {
//            if (listener == null) asynchronousUpdateComplete = true;
//            else                  listener.onModified(Recipient.this);
//          }
//        }
//      }
//
//      @Override
//      public void onFailure(Throwable error) {
//        Log.w("Recipient", error);
//      }
//    });
//  }

  public Recipient(String name, String number, Uri contactUri, Bitmap contactPhoto) {
    this.number = number;
    this.contactUri.set(contactUri);
    this.name.set(name);
    this.contactPhoto.set(contactPhoto);
  }

  public Recipient(Parcel in) {
    this.number = in.readString();

    this.name.set(in.readString());
    this.contactUri.set((Uri)in.readParcelable(null));
    this.contactPhoto.set((Bitmap)in.readParcelable(null));
  }

  public Uri getContactUri() {
    return this.contactUri.get();
  }

  public String getName() {
    return name.get();
  }

  public String getNumber() {
    return number;
  }

  public int describeContents() {
    return 0;
  }

  public void updateAsynchronousContent(RecipientDetails result) {
    if (result != null) {
      Recipient.this.name.set(result.name);
      Recipient.this.contactUri.set(result.contactUri);
      Recipient.this.contactPhoto.set(result.avatar);

      synchronized(this) {
        if (listener == null) asynchronousUpdateComplete = true;
        else                  listener.onModified(Recipient.this);
      }
    }
  }

  public synchronized void setListener(RecipientModifiedListener listener) {
    this.listener = listener;
    if (asynchronousUpdateComplete) {
      if (listener != null)
        listener.onModified(this);
      asynchronousUpdateComplete = false;
    }
  }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(number);
    dest.writeString(name.get());
    dest.writeParcelable(contactUri.get(), 0);
    dest.writeParcelable(contactPhoto.get(), 0);
  }

  public String toShortString() {
    return (name.get() == null ? number : name.get());
  }

  public Bitmap getContactPhoto() {
    return contactPhoto.get();
  }

  public static interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }
}
