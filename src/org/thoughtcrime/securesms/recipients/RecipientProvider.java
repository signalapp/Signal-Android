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
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.VibrateState;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

class RecipientProvider {

  private static final String TAG = RecipientProvider.class.getSimpleName();

  private static final RecipientCache  recipientCache         = new RecipientCache();
  private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER,
    PhoneLookup.LABEL
  };

  private static final Map<String, RecipientDetails> STATIC_DETAILS = new HashMap<String, RecipientDetails>() {{
    put("262966", new RecipientDetails("Amazon", null, null,
                                       ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_amazon),
                                       null, null));
  }};

  @NonNull Recipient getRecipient(Context context, Address address, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(address);
    if (cachedRecipient != null && !cachedRecipient.isStale() && (asynchronous || !cachedRecipient.isResolving())) {
      return cachedRecipient;
    }

    if (asynchronous) {
      cachedRecipient = new Recipient(address, cachedRecipient, getRecipientDetailsAsync(context, address));
    } else {
      cachedRecipient = new Recipient(address, getRecipientDetailsSync(context, address, false));
    }

    recipientCache.set(address, cachedRecipient);
    return cachedRecipient;
  }

  void clearCache() {
    recipientCache.reset();
  }

  private @NonNull ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context, final @NonNull Address address)
  {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetailsSync(context, address, true);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull RecipientDetails getRecipientDetailsSync(Context context, @NonNull Address address, boolean nestedAsynchronous) {
    if (address.isGroup()) return getGroupRecipientDetails(context, address, nestedAsynchronous);
    else                   return getIndividualRecipientDetails(context, address);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(Context context, @NonNull Address address) {
    Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(address);

    if (address.isPhone() && !TextUtils.isEmpty(address.toPhoneString())) {
      Uri    uri    = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address.toPhoneString()));
      Cursor cursor = context.getContentResolver().query(uri, CALLER_ID_PROJECTION, null, null, null);

      try {
        if (cursor != null && cursor.moveToFirst()) {
          final String resultNumber = cursor.getString(3);
          if (resultNumber != null) {
            Uri          contactUri   = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
            String       name         = resultNumber.equals(cursor.getString(0)) ? null : cursor.getString(0);
            ContactPhoto contactPhoto = ContactPhotoFactory.getContactPhoto(context,
                                                                            Uri.withAppendedPath(Contacts.CONTENT_URI, cursor.getLong(2) + ""),
                                                                            name);

            return new RecipientDetails(cursor.getString(0), cursor.getString(4), contactUri, contactPhoto, preferences.orNull(), null);
          } else {
            Log.w(TAG, "resultNumber is null");
          }
        }
      } finally {
        if (cursor != null)
          cursor.close();
      }
    }

    if (STATIC_DETAILS.containsKey(address.serialize())) return STATIC_DETAILS.get(address.serialize());
    else                                                 return new RecipientDetails(null, null, null, ContactPhotoFactory.getDefaultContactPhoto(null), preferences.orNull(), null);
  }

  private @NonNull RecipientDetails getGroupRecipientDetails(Context context, Address groupId, boolean asynchronous) {
    GroupDatabase.GroupRecord record = DatabaseFactory.getGroupDatabase(context).getGroup(groupId.toGroupString());

    if (record != null) {
      ContactPhoto    contactPhoto    = ContactPhotoFactory.getGroupContactPhoto(record.getAvatar());
      String          title           = record.getTitle();
      List<Address>   memberAddresses = record.getMembers();
      List<Recipient> members         = new LinkedList<>();

      for (Address memberAddress : memberAddresses) {
        members.add(getRecipient(context, memberAddress, asynchronous));
      }

      if (!groupId.isMmsGroup() && title == null) {
        title = context.getString(R.string.RecipientProvider_unnamed_group);;
      }

      return new RecipientDetails(title, null, null, contactPhoto, null, members);
    }

    return new RecipientDetails(context.getString(R.string.RecipientProvider_unnamed_group), null, null, ContactPhotoFactory.getDefaultGroupPhoto(), null, null);
  }

  static class RecipientDetails {
    @Nullable public final String          name;
    @Nullable public final String          customLabel;
    @NonNull  public final ContactPhoto    avatar;
    @Nullable public final Uri             contactUri;
    @Nullable public final MaterialColor   color;
    @Nullable public final Uri             ringtone;
              public final long            mutedUntil;
    @Nullable public final VibrateState    vibrateState;
              public final boolean         blocked;
              public final int             expireMessages;
    @NonNull  public final List<Recipient> participants;

    public RecipientDetails(@Nullable String name, @Nullable String customLabel,
                            @Nullable Uri contactUri, @NonNull ContactPhoto avatar,
                            @Nullable RecipientsPreferences preferences,
                            @Nullable List<Recipient> participants)
    {
      this.name           = name;
      this.customLabel    = customLabel;
      this.avatar         = avatar;
      this.contactUri     = contactUri;
      this.color          = preferences  != null ? preferences.getColor() : null;
      this.ringtone       = preferences  != null ? preferences.getRingtone() : null;
      this.mutedUntil     = preferences  != null ? preferences.getMuteUntil() : 0;
      this.vibrateState   = preferences  != null ? preferences.getVibrateState() : null;
      this.blocked        = preferences != null && preferences.isBlocked();
      this.expireMessages = preferences  != null ? preferences.getExpireMessages() : 0;
      this.participants   = participants == null ? new LinkedList<Recipient>() : participants;
    }
  }

  private static class RecipientCache {

    private final Map<Address,Recipient> cache = new LRUCache<>(1000);

    public synchronized Recipient get(Address address) {
      return cache.get(address);
    }

    public synchronized void set(Address address, Recipient recipient) {
      cache.put(address, recipient);
    }

    public synchronized void reset() {
      for (Recipient recipient : cache.values()) {
        recipient.setStale();
      }
    }

  }

}