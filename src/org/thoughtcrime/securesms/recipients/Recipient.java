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

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

public class Recipient {

  private final static String TAG = Recipient.class.getSimpleName();

  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final long recipientId;

  private @NonNull  String  number;
  private @Nullable String  name;
  private @Nullable String  customLabel;
  private boolean stale;
  private boolean resolving;

  private ContactPhoto contactPhoto;
  private Uri          contactUri;

  @Nullable private MaterialColor color;

  Recipient(long recipientId,
            @NonNull  String number,
            @Nullable Recipient stale,
            @NonNull  ListenableFutureTask<RecipientDetails> future)
  {
    this.recipientId  = recipientId;
    this.number       = number;
    this.contactPhoto = ContactPhotoFactory.getLoadingPhoto();
    this.color        = null;
    this.resolving    = true;

    if (stale != null) {
      this.name         = stale.name;
      this.contactUri   = stale.contactUri;
      this.contactPhoto = stale.contactPhoto;
      this.color        = stale.color;
      this.customLabel  = stale.customLabel;
    }

    future.addListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.name         = result.name;
            Recipient.this.number       = result.number;
            Recipient.this.contactUri   = result.contactUri;
            Recipient.this.contactPhoto = result.avatar;
            Recipient.this.color        = result.color;
            Recipient.this.customLabel  = result.customLabel;
            Recipient.this.resolving    = false;
          }

          notifyListeners();
        }
      }

      @Override
      public void onFailure(ExecutionException error) {
        Log.w(TAG, error);
      }
    });
  }

  Recipient(long recipientId, RecipientDetails details) {
    this.recipientId  = recipientId;
    this.number       = details.number;
    this.contactUri   = details.contactUri;
    this.name         = details.name;
    this.contactPhoto = details.avatar;
    this.color        = details.color;
    this.resolving    = false;
    this.customLabel  = details.customLabel;
  }

  public synchronized @Nullable Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized @Nullable String getName() {
    return this.name;
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (color != null) return color;
    else if (name != null)  return ContactColors.generateFor(name);
    else                    return ContactColors.UNKNOWN_COLOR;
  }

  public void setColor(@NonNull MaterialColor color) {
    synchronized (this) {
      this.color = color;
    }

    notifyListeners();
  }

  public @NonNull String getNumber() {
    return number;
  }

  public @Nullable String getCustomLabel() {
    return customLabel;
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

  public synchronized String toShortString() {
    return (name == null ? number : name);
  }

  public synchronized @NonNull ContactPhoto getContactPhoto() {
    return contactPhoto;
  }

  public static Recipient getUnknownRecipient() {
    return new Recipient(-1, new RecipientDetails("Unknown", "Unknown", null, null,
                                                  ContactPhotoFactory.getDefaultContactPhoto(null), null));
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

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(this);
  }

  public interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }

  boolean isStale() {
    return stale;
  }

  void setStale() {
    this.stale = true;
  }

  synchronized boolean isResolving() {
    return resolving;
  }

}
