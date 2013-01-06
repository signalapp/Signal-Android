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
import android.util.Log;

import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.util.HashSet;

public class Recipient implements Parcelable {

  public static final Parcelable.Creator<Recipient> CREATOR = new Parcelable.Creator<Recipient>() {
    public Recipient createFromParcel(Parcel in) {
      return new Recipient(in);
    }

    public Recipient[] newArray(int size) {
      return new Recipient[size];
    }
  };

  private final String number;
  private final HashSet<RecipientModifiedListener> listeners = new HashSet<RecipientModifiedListener>();

  private String name;
  private Bitmap contactPhoto;
  private Uri    contactUri;

  public Recipient(String number, Bitmap contactPhoto,
                   ListenableFutureTask<RecipientDetails> future)
  {
    this.number       = number;
    this.contactPhoto = contactPhoto;

    future.setListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          HashSet<RecipientModifiedListener> localListeners;

          synchronized (Recipient.this) {
            Recipient.this.name         = result.name;
            Recipient.this.contactUri   = result.contactUri;
            Recipient.this.contactPhoto = result.avatar;
            localListeners              = (HashSet<RecipientModifiedListener>)listeners.clone();
            listeners.clear();
          }

          for (RecipientModifiedListener listener : localListeners)
            listener.onModified(Recipient.this);
        }
      }

      @Override
      public void onFailure(Throwable error) {
        Log.w("Recipient", error);
      }
    });
  }

  public Recipient(String name, String number, Uri contactUri, Bitmap contactPhoto) {
    this.number       = number;
    this.contactUri   = contactUri;
    this.name         = name;
    this.contactPhoto = contactPhoto;
  }

  public Recipient(Parcel in) {
    this.number       = in.readString();
    this.name         = in.readString();
    this.contactUri   = (Uri)in.readParcelable(null);
    this.contactPhoto = (Bitmap)in.readParcelable(null);
  }

  public synchronized Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized String getName() {
    return this.name;
  }

  public String getNumber() {
    return number;
  }

  public int describeContents() {
    return 0;
  }

//  public void updateAsynchronousContent(RecipientDetails result) {
//    if (result != null) {
//      Recipient.this.name.set(result.name);
//      Recipient.this.contactUri.set(result.contactUri);
//      Recipient.this.contactPhoto.set(result.avatar);
//
//      synchronized(this) {
//        if (listener == null) asynchronousUpdateComplete = true;
//        else                  listener.onModified(Recipient.this);
//      }
//    }
//  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public synchronized void writeToParcel(Parcel dest, int flags) {
    dest.writeString(number);
    dest.writeString(name);
    dest.writeParcelable(contactUri, 0);
    dest.writeParcelable(contactPhoto, 0);
  }

  public synchronized String toShortString() {
    return (name == null ? number : name);
  }

  public synchronized Bitmap getContactPhoto() {
    return contactPhoto;
  }

  public static interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }
}
