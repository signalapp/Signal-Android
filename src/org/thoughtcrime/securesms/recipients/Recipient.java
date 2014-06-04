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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.textsecure.storage.CanonicalRecipient;
import org.whispersystems.textsecure.util.FutureTaskListener;
import org.whispersystems.textsecure.util.ListenableFutureTask;

import java.util.HashSet;

public class Recipient implements Parcelable, CanonicalRecipient {

  private final static String TAG = Recipient.class.getSimpleName();

  public static final Parcelable.Creator<Recipient> CREATOR = new Parcelable.Creator<Recipient>() {
    public Recipient createFromParcel(Parcel in) {
      return new Recipient(in);
    }

    public Recipient[] newArray(int size) {
      return new Recipient[size];
    }
  };

  private final HashSet<RecipientModifiedListener> listeners = new HashSet<RecipientModifiedListener>();

  private final String number;
  private final long   recipientId;

  private String name;
  private Bitmap contactPhoto;
  private Uri    contactUri;

  private class RecipientDetailsFutureTaskListener implements FutureTaskListener<RecipientDetails> {
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
  }

  Recipient(String number, Bitmap contactPhoto, long recipientId,
            ListenableFutureTask<RecipientDetails> future)
  {
    this.number       = number;
    this.contactPhoto = contactPhoto;
    this.recipientId  = recipientId;

    future.setListener(new RecipientDetailsFutureTaskListener());
  }

  Recipient(String name, String number, long recipientId, Uri contactUri, Bitmap contactPhoto) {
    this.number       = number;
    this.recipientId  = recipientId;
    this.contactUri   = contactUri;
    this.name         = name;
    this.contactPhoto = contactPhoto;
  }

  public Recipient(Parcel in) {
    this.number       = in.readString();
    this.name         = in.readString();
    this.recipientId  = in.readLong();
    this.contactUri   = in.readParcelable(null);
    this.contactPhoto = in.readParcelable(null);
  }

  public synchronized Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized void setContactPhoto(Bitmap bitmap) {
    this.contactPhoto = bitmap;
    notifyListeners();
  }

  public synchronized void setName(String name) {
    this.name = name;
    notifyListeners();
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

  public long getRecipientId() {
    return recipientId;
  }

  public boolean isGroupRecipient() {
    return GroupUtil.isEncodedGroup(number);
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public void notifyListeners() {
    HashSet<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = (HashSet<RecipientModifiedListener>)listeners.clone();
    }

    for (RecipientModifiedListener listener : localListeners) {
      listener.onModified(this);
    }
  }

  public synchronized void writeToParcel(Parcel dest, int flags) {
    dest.writeString(number);
    dest.writeString(name);
    dest.writeLong(recipientId);
    dest.writeParcelable(contactUri, 0);
    dest.writeParcelable(contactPhoto, 0);
  }

  public synchronized String toShortString() {
    return (name == null ? number : name);
  }

  public synchronized Bitmap getContactPhoto() {
    return contactPhoto;
  }

  public static Recipient getUnknownRecipient(Context context) {
    return new Recipient("Unknown", "Unknown", -1, null, ContactPhotoFactory.getDefaultContactPhoto(context));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return 31 + (int)this.recipientId;
  }

  public static interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }
}
