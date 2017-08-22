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
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
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

  @NonNull Recipient getRecipient(Context context, Address address, Optional<RecipientSettings> settings, Optional<GroupRecord> groupRecord, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(address);
    if (cachedRecipient != null && !cachedRecipient.isStale() && (asynchronous || !cachedRecipient.isResolving())) {
      return cachedRecipient;
    }

    Optional<RecipientDetails> prefetchedRecipientDetails = createPrefetchedRecipientDetails(context, address, settings, groupRecord);

    if (asynchronous) {
      cachedRecipient = new Recipient(address, cachedRecipient, prefetchedRecipientDetails, getRecipientDetailsAsync(context, address, settings, groupRecord));
    } else {
      cachedRecipient = new Recipient(address, getRecipientDetailsSync(context, address, settings, groupRecord, false));
    }

    recipientCache.set(address, cachedRecipient);
    return cachedRecipient;
  }

  void clearCache() {
    recipientCache.reset();
  }

  private @NonNull Optional<RecipientDetails> createPrefetchedRecipientDetails(@NonNull Context context, @NonNull Address address,
                                                                               @NonNull Optional<RecipientSettings> settings,
                                                                               @NonNull Optional<GroupRecord> groupRecord)
  {
    if (address.isGroup() && settings.isPresent() && groupRecord.isPresent()) {
      return Optional.of(getGroupRecipientDetails(context, address, groupRecord, settings, true));
    } else if (!address.isGroup() && settings.isPresent()) {
      return Optional.of(new RecipientDetails(null, null, null, ContactPhotoFactory.getLoadingPhoto(), settings.get(), null));
    }

    return Optional.absent();
  }

  private @NonNull ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context, final @NonNull Address address, final @NonNull Optional<RecipientSettings> settings, final @NonNull Optional<GroupRecord> groupRecord)
  {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetailsSync(context, address, settings, groupRecord, true);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull RecipientDetails getRecipientDetailsSync(Context context, @NonNull Address address, Optional<RecipientSettings> settings, Optional<GroupRecord> groupRecord, boolean nestedAsynchronous) {
    if (address.isGroup()) return getGroupRecipientDetails(context, address, groupRecord, settings, nestedAsynchronous);
    else                   return getIndividualRecipientDetails(context, address, settings);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(Context context, @NonNull Address address, Optional<RecipientSettings> settings) {
    if (!settings.isPresent()) {
      settings = DatabaseFactory.getRecipientDatabase(context).getRecipientSettings(address);
    }

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
                                                                            address,
                                                                            name);

            return new RecipientDetails(cursor.getString(0), cursor.getString(4), contactUri, contactPhoto, settings.orNull(), null);
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
    else                                                 return new RecipientDetails(null, null, null, ContactPhotoFactory.getSignalAvatarContactPhoto(context, address, null, context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size)), settings.orNull(), null);
  }

  private @NonNull RecipientDetails getGroupRecipientDetails(Context context, Address groupId, Optional<GroupRecord> groupRecord, Optional<RecipientSettings> settings, boolean asynchronous) {
    if (!groupRecord.isPresent()) {
      groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(groupId.toGroupString());
    }

    if (!settings.isPresent()) {
      settings = DatabaseFactory.getRecipientDatabase(context).getRecipientSettings(groupId);
    }

    if (groupRecord.isPresent()) {
      ContactPhoto    contactPhoto    = ContactPhotoFactory.getGroupContactPhoto(groupRecord.get().getAvatar());
      String          title           = groupRecord.get().getTitle();
      List<Address>   memberAddresses = groupRecord.get().getMembers();
      List<Recipient> members         = new LinkedList<>();

      for (Address memberAddress : memberAddresses) {
        members.add(getRecipient(context, memberAddress, Optional.absent(), Optional.absent(), asynchronous));
      }

      if (!groupId.isMmsGroup() && title == null) {
        title = context.getString(R.string.RecipientProvider_unnamed_group);;
      }

      return new RecipientDetails(title, null, null, contactPhoto, settings.orNull(), members);
    }

    return new RecipientDetails(context.getString(R.string.RecipientProvider_unnamed_group), null, null, ContactPhotoFactory.getDefaultGroupPhoto(), settings.orNull(), null);
  }

  static class RecipientDetails {
    @Nullable public final String            name;
    @Nullable public final String            customLabel;
    @NonNull  public final ContactPhoto      avatar;
    @Nullable public final Uri               contactUri;
    @Nullable public final MaterialColor     color;
    @Nullable public final Uri               ringtone;
              public final long              mutedUntil;
    @Nullable public final VibrateState      vibrateState;
              public final boolean           blocked;
              public final int               expireMessages;
    @NonNull  public final List<Recipient>   participants;
    @Nullable public final String            profileName;
              public final boolean           seenInviteReminder;
              public final Optional<Integer> defaultSubscriptionId;
    @NonNull  public final RegisteredState   registered;
    @Nullable public final byte[]            profileKey;
    @Nullable public final String            profileAvatar;
              public final boolean           profileSharing;
              public final boolean           systemContact;

    public RecipientDetails(@Nullable String name, @Nullable String customLabel,
                            @Nullable Uri contactUri, @NonNull ContactPhoto avatar,
                            @Nullable RecipientSettings settings,
                            @Nullable List<Recipient> participants)
    {
      this.customLabel           = customLabel;
      this.avatar                = avatar;
      this.contactUri            = contactUri;
      this.color                 = settings     != null ? settings.getColor() : null;
      this.ringtone              = settings     != null ? settings.getRingtone() : null;
      this.mutedUntil            = settings     != null ? settings.getMuteUntil() : 0;
      this.vibrateState          = settings     != null ? settings.getVibrateState() : null;
      this.blocked               = settings != null && settings.isBlocked();
      this.expireMessages        = settings     != null ? settings.getExpireMessages() : 0;
      this.participants          = participants == null ? new LinkedList<Recipient>() : participants;
      this.profileName           = settings     != null ? settings.getProfileName() : null;
      this.seenInviteReminder    = settings != null && settings.hasSeenInviteReminder();
      this.defaultSubscriptionId = settings     != null ? settings.getDefaultSubscriptionId() : Optional.absent();
      this.registered            = settings     != null ? settings.getRegistered() : RegisteredState.UNKNOWN;
      this.profileKey            = settings     != null ? settings.getProfileKey() : null;
      this.profileAvatar         = settings     != null ? settings.getProfileAvatar() : null;
      this.profileSharing        = settings != null && settings.isProfileSharing();
      this.systemContact         = settings != null && !TextUtils.isEmpty(settings.getSystemDisplayName());

      if (name == null && settings != null) this.name = settings.getSystemDisplayName();
      else                                  this.name = name;
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