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
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.recipients.RecipientProvider.RecipientDetails;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

public class Recipient implements RecipientModifiedListener {

  public static final  String            RECIPIENT_CLEAR_ACTION = "org.thoughtcrime.securesms.database.RecipientFactory.CLEAR";
  private static final String            TAG                    = Recipient.class.getSimpleName();
  private static final RecipientProvider provider               = new RecipientProvider();

  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final @NonNull Address address;
  private final @NonNull List<Recipient> participants = new LinkedList<>();

  private @Nullable String  name;
  private @Nullable String  customLabel;
  private           boolean stale;
  private           boolean resolving;

  private ContactPhoto contactPhoto;
  private Uri          contactUri;
  private Uri          ringtone       = null;
  private long         mutedUntil     = 0;
  private boolean      blocked        = false;
  private VibrateState vibrate        = VibrateState.DEFAULT;
  private int          expireMessages = 0;
  private String       profileName    = null;

  @Nullable private MaterialColor color;

  public static @NonNull Recipient from(@NonNull Context context, @NonNull Address address, boolean asynchronous) {
    if (address == null) throw new AssertionError(address);
    return provider.getRecipient(context, address, Optional.absent(), Optional.absent(), asynchronous);
  }

  public static @NonNull Recipient from(@NonNull Context context, @NonNull Address address, @NonNull Optional<RecipientSettings> settings, @NonNull Optional<GroupDatabase.GroupRecord> groupRecord, boolean asynchronous) {
    if (address == null) throw new AssertionError(address);
    return provider.getRecipient(context, address, settings, groupRecord, asynchronous);
  }

  public static void clearCache(Context context) {
    provider.clearCache();
    context.sendBroadcast(new Intent(RECIPIENT_CLEAR_ACTION));
  }

  Recipient(@NonNull  Address address,
            @Nullable Recipient stale,
            @NonNull  Optional<RecipientDetails> details,
            @NonNull  ListenableFutureTask<RecipientDetails> future)
  {
    this.address      = address;
    this.contactPhoto = ContactPhotoFactory.getLoadingPhoto();
    this.color        = null;
    this.resolving    = true;

    if (stale != null) {
      this.name           = stale.name;
      this.contactUri     = stale.contactUri;
      this.contactPhoto   = stale.contactPhoto;
      this.color          = stale.color;
      this.customLabel    = stale.customLabel;
      this.ringtone       = stale.ringtone;
      this.mutedUntil     = stale.mutedUntil;
      this.blocked        = stale.blocked;
      this.vibrate        = stale.vibrate;
      this.expireMessages = stale.expireMessages;
      this.profileName    = stale.profileName;
      this.participants.clear();
      this.participants.addAll(stale.participants);
    }

    if (details.isPresent()) {
      this.name           = details.get().name;
      this.contactPhoto   = details.get().avatar;
      this.color          = details.get().color;
      this.ringtone       = details.get().ringtone;
      this.mutedUntil     = details.get().mutedUntil;
      this.blocked        = details.get().blocked;
      this.vibrate        = details.get().vibrateState;
      this.expireMessages = details.get().expireMessages;
      this.profileName    = details.get().profileName;
      this.participants.clear();
      this.participants.addAll(details.get().participants);
    }

    future.addListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.name           = result.name;
            Recipient.this.contactUri     = result.contactUri;
            Recipient.this.contactPhoto   = result.avatar;
            Recipient.this.color          = result.color;
            Recipient.this.customLabel    = result.customLabel;
            Recipient.this.ringtone       = result.ringtone;
            Recipient.this.mutedUntil     = result.mutedUntil;
            Recipient.this.blocked        = result.blocked;
            Recipient.this.vibrate        = result.vibrateState;
            Recipient.this.expireMessages = result.expireMessages;
            Recipient.this.profileName    = result.profileName;

            Recipient.this.participants.clear();
            Recipient.this.participants.addAll(result.participants);
            Recipient.this.resolving      = false;

            if (!listeners.isEmpty()) {
              for (Recipient recipient : participants) recipient.addListener(Recipient.this);
            }
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

  Recipient(@NonNull Address address, @NonNull RecipientDetails details) {
    this.address        = address;
    this.contactUri     = details.contactUri;
    this.name           = details.name;
    this.contactPhoto   = details.avatar;
    this.color          = details.color;
    this.customLabel    = details.customLabel;
    this.ringtone       = details.ringtone;
    this.mutedUntil     = details.mutedUntil;
    this.blocked        = details.blocked;
    this.vibrate        = details.vibrateState;
    this.expireMessages = details.expireMessages;
    this.profileName    = details.profileName;
    this.participants.addAll(details.participants);
    this.resolving    = false;
  }

  public synchronized @Nullable Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized @Nullable String getName() {
    if (this.name == null && isMmsGroupRecipient()) {
      List<String> names = new LinkedList<>();

      for (Recipient recipient : participants) {
        names.add(recipient.toShortString());
      }

      return Util.join(names, ", ");
    }

    return this.name;
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (isGroupRecipient()) return MaterialColor.GROUP;
    else if (color != null)      return color;
    else if (name != null)       return ContactColors.generateFor(name);
    else                         return ContactColors.UNKNOWN_COLOR;
  }

  public void setColor(@NonNull MaterialColor color) {
    synchronized (this) {
      this.color = color;
    }

    notifyListeners();
  }

  public @NonNull Address getAddress() {
    return address;
  }

  public @Nullable String getCustomLabel() {
    return customLabel;
  }

  public @Nullable String getProfileName() {
    return profileName;
  }

  public boolean isGroupRecipient() {
    return address.isGroup();
  }

  public boolean isMmsGroupRecipient() {
    return address.isMmsGroup();
  }

  public boolean isPushGroupRecipient() {
    return address.isGroup() && !address.isMmsGroup();
  }

  public @NonNull List<Recipient> getParticipants() {
    return participants;
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    if (listeners.isEmpty()) {
      for (Recipient recipient : participants) recipient.addListener(this);
    }
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);

    if (listeners.isEmpty()) {
      for (Recipient recipient : participants) recipient.removeListener(this);
    }
  }

  public synchronized String toShortString() {
    return (getName() == null ? address.serialize() : getName());
  }

  public synchronized @NonNull ContactPhoto getContactPhoto() {
    return contactPhoto;
  }

  public synchronized @Nullable Uri getRingtone() {
    return ringtone;
  }

  public void setRingtone(Uri ringtone) {
    synchronized (this) {
      this.ringtone = ringtone;
    }

    notifyListeners();
  }

  public synchronized boolean isMuted() {
    return System.currentTimeMillis() <= mutedUntil;
  }

  public void setMuted(long mutedUntil) {
    synchronized (this) {
      this.mutedUntil = mutedUntil;
    }

    notifyListeners();
  }

  public synchronized boolean isBlocked() {
    return blocked;
  }

  public void setBlocked(boolean blocked) {
    synchronized (this) {
      this.blocked = blocked;
    }

    notifyListeners();
  }

  public synchronized VibrateState getVibrate() {
    return vibrate;
  }

  public void setVibrate(VibrateState vibrate) {
    synchronized (this) {
      this.vibrate = vibrate;
    }

    notifyListeners();
  }

  public synchronized int getExpireMessages() {
    return expireMessages;
  }

  public void setExpireMessages(int expireMessages) {
    synchronized (this) {
      this.expireMessages = expireMessages;
    }

    notifyListeners();
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.address.equals(that.address);
  }

  @Override
  public int hashCode() {
    return this.address.hashCode();
  }

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(this);
  }

  @Override
  public void onModified(Recipient recipient) {
    notifyListeners();
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
