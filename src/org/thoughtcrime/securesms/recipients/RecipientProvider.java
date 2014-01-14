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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.util.LRUCache;
import org.whispersystems.textsecure.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class RecipientProvider {

  private static final Map<String,Recipient> recipientCache   = Collections.synchronizedMap(new LRUCache<String,Recipient>(1000));
  private static final ExecutorService asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
  };

  public Recipient getRecipient(Context context, String number, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(number);

    if (cachedRecipient != null) return cachedRecipient;
    else if (asynchronous)       return getAsynchronousRecipient(context, number);
    else                         return getSynchronousRecipient(context, number);
  }

  public Recipient getGroupRecipient(Context context, String groupId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(groupId);

    if (cachedRecipient != null) return cachedRecipient;
    else if (asynchronous) return getAsynchronousGroupRecipient(context, groupId);
    else                   return getSynchronousGroupRecipient(context, groupId);
  }

  private Recipient getSynchronousRecipient(Context context, String number) {
    Log.w("RecipientProvider", "Cache miss [SYNC]!");
    RecipientDetails details = getRecipientDetails(context, number);
    Recipient recipient;

    if (details != null) {
      recipient = new Recipient(details.name, number, details.contactUri, details.avatar);
    } else {
      recipient = new Recipient(null, number, null, ContactPhotoFactory.getDefaultContactPhoto(context));
    }

    recipientCache.put(number, recipient);
    return recipient;
  }

  private Recipient getSynchronousGroupRecipient(Context context, String groupId) {
    RecipientDetails details = getGroupRecipientDetails(context, groupId);
    Recipient recipient;

    if (details != null) {
      recipient = new Recipient(details.name, groupId, details.contactUri, details.avatar);
    } else {
      recipient = new Recipient(null, groupId, null, ContactPhotoFactory.getDefaultContactPhoto(context));
    }

    recipientCache.put(groupId, recipient);
    return recipient;
  }

  private Recipient getAsynchronousRecipient(final Context context, final String number) {
    Log.w("RecipientProvider", "Cache miss [ASYNC]!");

    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetails(context, number);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<RecipientDetails>(task, null);

    asyncRecipientResolver.submit(future);

    Recipient recipient = new Recipient(number, ContactPhotoFactory.getDefaultContactPhoto(context), future);
    recipientCache.put(number, recipient);

    return recipient;
  }

  private Recipient getAsynchronousGroupRecipient(final Context context, final String groupId) {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getGroupRecipientDetails(context, groupId);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<RecipientDetails>(task, null);

    asyncRecipientResolver.submit(future);

    Recipient recipient = new Recipient(groupId, ContactPhotoFactory.getDefaultContactPhoto(context), future);
    recipientCache.put(groupId, recipient);

    return recipient;
  }

  public void clearCache() {
    recipientCache.clear();
  }

  private RecipientDetails getRecipientDetails(Context context, String number) {
    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = context.getContentResolver().query(uri, CALLER_ID_PROJECTION,
                                                       null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        Uri contactUri      = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
        Bitmap contactPhoto = getContactPhoto(context, Uri.withAppendedPath(Contacts.CONTENT_URI,
                                                                            cursor.getLong(2)+""));

        return new RecipientDetails(cursor.getString(0), contactUri, contactPhoto);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  private RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    GroupDatabase.Reader reader = DatabaseFactory.getGroupDatabase(context).getGroup(groupId.substring(2));
    GroupDatabase.GroupRecord record;

    try {
      if ((record = reader.getNext()) != null) {
        byte[] avatarBytes = record.getAvatar();
        Bitmap avatar;

        if (avatarBytes == null) avatar = ContactPhotoFactory.getDefaultContactPhoto(context);
        else                     avatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);

        return new RecipientDetails(record.getTitle(), null, avatar);
      }
    } finally {
      reader.close();
    }

    return null;
  }

  private Bitmap getContactPhoto(Context context, Uri uri) {
    InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);

    if (inputStream == null)
      return ContactPhotoFactory.getDefaultContactPhoto(context);
    else
      return BitmapFactory.decodeStream(inputStream);
  }

  public static class RecipientDetails {
    public final String name;
    public final Bitmap avatar;
    public final Uri contactUri;

    public RecipientDetails(String name, Uri contactUri, Bitmap avatar) {
      this.name       = name;
      this.avatar     = avatar;
      this.contactUri = contactUri;
    }
  }

}