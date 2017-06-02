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
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.CanonicalAddressDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

class RecipientProvider {

  private static final String TAG = RecipientProvider.class.getSimpleName();

  private static final RecipientCache  recipientCache         = new RecipientCache();
  private static final RecipientsCache recipientsCache        = new RecipientsCache();
  private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER,
    PhoneLookup.LABEL
  };

  private static final Map<String, RecipientDetails> STATIC_DETAILS = new HashMap<String, RecipientDetails>() {{
    put("262966", new RecipientDetails("Amazon", "262966", null, null,
                                       ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_amazon),
                                       ContactColors.UNKNOWN_COLOR));
  }};

  @NonNull Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);
    if (cachedRecipient != null && !cachedRecipient.isStale() && (asynchronous || !cachedRecipient.isResolving())) {
      return cachedRecipient;
    }

    String number = CanonicalAddressDatabase.getInstance(context).getAddressFromId(recipientId);

    if (asynchronous) {
      cachedRecipient = new Recipient(recipientId, number, cachedRecipient, getRecipientDetailsAsync(context, recipientId, number));
    } else {
      cachedRecipient = new Recipient(recipientId, getRecipientDetailsSync(context, recipientId, number));
    }

    recipientCache.set(recipientId, cachedRecipient);
    return cachedRecipient;
  }

  @NonNull Recipients getRecipients(Context context, long[] recipientIds, boolean asynchronous) {
    Recipients cachedRecipients = recipientsCache.get(new RecipientIds(recipientIds));
    if (cachedRecipients != null && !cachedRecipients.isStale() && (asynchronous || !cachedRecipients.isResolving())) {
      return cachedRecipients;
    }

    List<Recipient> recipientList = new LinkedList<>();

    for (long recipientId : recipientIds) {
      recipientList.add(getRecipient(context, recipientId, asynchronous));
    }

    if (asynchronous) cachedRecipients = new Recipients(recipientList, cachedRecipients, getRecipientsPreferencesAsync(context, recipientIds));
    else              cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesSync(context, recipientIds));

    recipientsCache.set(new RecipientIds(recipientIds), cachedRecipients);
    return cachedRecipients;
  }

  void clearCache() {
    recipientCache.reset();
    recipientsCache.reset();
  }

  private @NonNull ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context,
                                                                                   final long recipientId,
                                                                                   final @NonNull String number)
  {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetailsSync(context, recipientId, number);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull RecipientDetails getRecipientDetailsSync(Context context, long recipientId, @NonNull String number) {
    if (GroupUtil.isEncodedGroup(number)) return getGroupRecipientDetails(context, number);
    else                                  return getIndividualRecipientDetails(context, recipientId, number);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(Context context, long recipientId, @NonNull String number) {
    Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(new long[]{recipientId});
    MaterialColor                   color       = preferences.isPresent() ? preferences.get().getColor() : null;
    Uri                             uri         = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor                          cursor      = context.getContentResolver().query(uri, CALLER_ID_PROJECTION,
                                                                                     null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        final String resultNumber = cursor.getString(3);
        if (resultNumber != null) {
          Uri          contactUri   = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
          String       name         = resultNumber.equals(cursor.getString(0)) ? null : cursor.getString(0);
          ContactPhoto contactPhoto = ContactPhotoFactory.getContactPhoto(context,
                                                                          Uri.withAppendedPath(Contacts.CONTENT_URI, cursor.getLong(2) + ""),
                                                                          name);

          return new RecipientDetails(cursor.getString(0), resultNumber, cursor.getString(4), contactUri, contactPhoto, color);
        } else {
          Log.w(TAG, "resultNumber is null");
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    if (STATIC_DETAILS.containsKey(number)) return STATIC_DETAILS.get(number);
    else                                    return new RecipientDetails(null, number, null, null, ContactPhotoFactory.getDefaultContactPhoto(null), color);
  }

  private @NonNull RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    try {
      GroupDatabase.GroupRecord record = DatabaseFactory.getGroupDatabase(context)
                                                        .getGroup(GroupUtil.getDecodedId(groupId));

      if (record != null) {
        ContactPhoto contactPhoto = ContactPhotoFactory.getGroupContactPhoto(record.getAvatar());
        String       title        = record.getTitle();

        if (title == null) {
          title = context.getString(R.string.RecipientProvider_unnamed_group);
        }

        return new RecipientDetails(title, groupId, null, null, contactPhoto, null);
      }

      return new RecipientDetails(context.getString(R.string.RecipientProvider_unnamed_group), groupId, null, null, ContactPhotoFactory.getDefaultGroupPhoto(), null);
    } catch (IOException e) {
      Log.w("RecipientProvider", e);
      return new RecipientDetails(context.getString(R.string.RecipientProvider_unnamed_group), groupId, null, null, ContactPhotoFactory.getDefaultGroupPhoto(), null);
    }
  }

  private @Nullable RecipientsPreferences getRecipientsPreferencesSync(Context context, long[] recipientIds) {
    return DatabaseFactory.getRecipientPreferenceDatabase(context)
                          .getRecipientsPreferences(recipientIds)
                          .orNull();
  }

  private ListenableFutureTask<RecipientsPreferences> getRecipientsPreferencesAsync(final Context context, final long[] recipientIds) {
    ListenableFutureTask<RecipientsPreferences> task = new ListenableFutureTask<>(new Callable<RecipientsPreferences>() {
      @Override
      public RecipientsPreferences call() throws Exception {
        return getRecipientsPreferencesSync(context, recipientIds);
      }
    });

    asyncRecipientResolver.execute(task);

    return task;
  }

  public static class RecipientDetails {
    @Nullable public final String        name;
    @NonNull  public final String        number;
    @Nullable public final String        customLabel;
    @NonNull  public final ContactPhoto  avatar;
    @Nullable public final Uri           contactUri;
    @Nullable public final MaterialColor color;

    public RecipientDetails(@Nullable String name, @NonNull String number,
                            @Nullable String customLabel, @Nullable Uri contactUri,
                            @NonNull ContactPhoto avatar, @Nullable MaterialColor color)
    {
      this.name         = name;
      this.customLabel  = customLabel;
      this.number       = number;
      this.avatar       = avatar;
      this.contactUri   = contactUri;
      this.color        = color;
    }
  }

  private static class RecipientIds {
    private final long[] ids;

    private RecipientIds(long[] ids) {
      this.ids = ids;
    }

    public boolean equals(Object other) {
      if (other == null || !(other instanceof RecipientIds)) return false;
      return Arrays.equals(this.ids, ((RecipientIds) other).ids);
    }

    public int hashCode() {
      return Arrays.hashCode(ids);
    }
  }

  private static class RecipientCache {

    private final Map<Long,Recipient> cache = new LRUCache<>(1000);

    public synchronized Recipient get(long recipientId) {
      return cache.get(recipientId);
    }

    public synchronized void set(long recipientId, Recipient recipient) {
      cache.put(recipientId, recipient);
    }

    public synchronized void reset() {
      for (Recipient recipient : cache.values()) {
        recipient.setStale();
      }
    }

  }

  private static class RecipientsCache {

    private final Map<RecipientIds,Recipients> cache = new LRUCache<>(1000);

    public synchronized Recipients get(RecipientIds ids) {
      return cache.get(ids);
    }

    public synchronized void set(RecipientIds ids, Recipients recipients) {
      cache.put(ids, recipients);
    }

    public synchronized void reset() {
      for (Recipients recipients : cache.values()) {
        recipients.setStale();
      }
    }

  }



}