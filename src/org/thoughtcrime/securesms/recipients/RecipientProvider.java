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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.CanonicalAddressDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class RecipientProvider {

  private static final Map<Long,Recipient>          recipientCache         = Collections.synchronizedMap(new LRUCache<Long,Recipient>(1000));
  private static final Map<RecipientIds,Recipients> recipientsCache        = Collections.synchronizedMap(new LRUCache<RecipientIds, Recipients>(1000));
  private static final ExecutorService              asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER
  };

  Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);

    if      (cachedRecipient != null) return cachedRecipient;
    else if (asynchronous)            return getAsynchronousRecipient(context, recipientId);
    else                              return getSynchronousRecipient(context, recipientId);
  }

  Recipients getRecipients(Context context, long[] recipientIds, boolean asynchronous) {
    Recipients cachedRecipients = recipientsCache.get(new RecipientIds(recipientIds));
    if (cachedRecipients != null) return cachedRecipients;

    List<Recipient> recipientList = new LinkedList<>();

    for (long recipientId : recipientIds) {
      recipientList.add(getRecipient(context, recipientId, false));
    }

    if (asynchronous) cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesAsync(context, recipientIds));
    else              cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesSync(context, recipientIds));

    recipientsCache.put(new RecipientIds(recipientIds), cachedRecipients);
    return cachedRecipients;
  }

  private Recipient getSynchronousRecipient(final Context context, final long recipientId) {
    Log.w("RecipientProvider", "Cache miss [SYNC]!");

    final Recipient recipient;
    RecipientDetails details;
    String number = CanonicalAddressDatabase.getInstance(context).getAddressFromId(recipientId);
    final boolean isGroupRecipient = GroupUtil.isEncodedGroup(number);

    if (isGroupRecipient) details = getGroupRecipientDetails(context, number);
    else                  details = getRecipientDetails(context, number);

    if (details != null) {
      recipient = new Recipient(details.name, details.number, recipientId, details.contactUri, details.avatar);
    } else {
      final Drawable defaultPhoto        = isGroupRecipient
                                           ? ContactPhotoFactory.getDefaultGroupPhoto(context)
                                           : ContactPhotoFactory.getDefaultContactPhoto(context, null);

      recipient = new Recipient(null, number, recipientId, null, defaultPhoto);
    }

    recipientCache.put(recipientId, recipient);
    return recipient;
  }

  private Recipient getAsynchronousRecipient(final Context context, final long recipientId) {
    Log.w("RecipientProvider", "Cache miss [ASYNC]!");

    final String number = CanonicalAddressDatabase.getInstance(context).getAddressFromId(recipientId);
    final boolean isGroupRecipient = GroupUtil.isEncodedGroup(number);

    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        if (isGroupRecipient) return getGroupRecipientDetails(context, number);
        else                  return getRecipientDetails(context, number);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);

    asyncRecipientResolver.submit(future);

    Drawable contactPhoto;

    if (isGroupRecipient) {
      contactPhoto        = ContactPhotoFactory.getDefaultGroupPhoto(context);
    } else {
      contactPhoto        = ContactPhotoFactory.getLoadingPhoto(context);
    }

    Recipient recipient = new Recipient(number, contactPhoto, recipientId, future);
    recipientCache.put(recipientId, recipient);

    return recipient;
  }

  void clearCache() {
    recipientCache.clear();
    recipientsCache.clear();
  }

  private RecipientDetails getRecipientDetails(Context context, String number) {
    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = context.getContentResolver().query(uri, CALLER_ID_PROJECTION,
                                                       null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        Uri      contactUri   = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
        String   name         = cursor.getString(3).equals(cursor.getString(0)) ? null : cursor.getString(0);
        Drawable contactPhoto = ContactPhotoFactory.getContactPhoto(context,
                                                                    Uri.withAppendedPath(Contacts.CONTENT_URI, cursor.getLong(2) + ""),
                                                                    name);
        return new RecipientDetails(cursor.getString(0), cursor.getString(3), contactUri, contactPhoto);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return new RecipientDetails(null, number, null, ContactPhotoFactory.getDefaultContactPhoto(context, null));
  }

  private RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    try {
      GroupDatabase.GroupRecord record  = DatabaseFactory.getGroupDatabase(context)
                                                         .getGroup(GroupUtil.getDecodedId(groupId));

      if (record != null) {
        Drawable avatar = ContactPhotoFactory.getGroupContactPhoto(context, record.getAvatar());
        return new RecipientDetails(record.getTitle(), groupId, null, avatar);
      }

      return null;
    } catch (IOException e) {
      Log.w("RecipientProvider", e);
      return null;
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
    public final String   name;
    public final String   number;
    public final Drawable avatar;
    public final Uri      contactUri;

    public RecipientDetails(String name, String number, Uri contactUri, Drawable avatar) {
      this.name          = name;
      this.number        = number;
      this.avatar        = avatar;
      this.contactUri    = contactUri;
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



}