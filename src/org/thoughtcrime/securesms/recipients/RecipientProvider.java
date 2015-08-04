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
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.CanonicalAddressDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class RecipientProvider {

  private static final Map<Long,Recipient> recipientCache         = Collections.synchronizedMap(new LRUCache<Long,Recipient>(1000));
  private static final ExecutorService     asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER
  };

  public Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);

    if (cachedRecipient != null) return cachedRecipient;
    else if (asynchronous)       return getAsynchronousRecipient(context, recipientId);
    else                         return getSynchronousRecipient(context, recipientId);
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
      recipient = new Recipient(details.name, details.number, recipientId, details.contactUri, details.avatar,
                                details.croppedAvatar);
    } else {
      final Bitmap defaultPhoto        = isGroupRecipient
                                           ? ContactPhotoFactory.getDefaultGroupPhoto(context)
                                           : ContactPhotoFactory.getDefaultContactPhoto(context);
      final Bitmap defaultCroppedPhoto = isGroupRecipient
                                           ? ContactPhotoFactory.getDefaultGroupPhotoCropped(context)
                                           : ContactPhotoFactory.getDefaultContactPhotoCropped(context);

      recipient = new Recipient(null, number, recipientId, null, defaultPhoto, defaultCroppedPhoto);
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

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<RecipientDetails>(task);

    asyncRecipientResolver.submit(future);

    Bitmap contactPhoto;
    Bitmap contactPhotoCropped;

    if (isGroupRecipient) {
      contactPhoto        = ContactPhotoFactory.getDefaultGroupPhoto(context);
      contactPhotoCropped = ContactPhotoFactory.getDefaultGroupPhotoCropped(context);
    } else {
      contactPhoto        = ContactPhotoFactory.getDefaultContactPhoto(context);
      contactPhotoCropped = ContactPhotoFactory.getDefaultContactPhotoCropped(context);
    }

    Recipient recipient = new Recipient(number, contactPhoto, contactPhotoCropped, recipientId, future);
    recipientCache.put(recipientId, recipient);

    return recipient;
  }

  public void clearCache() {
    recipientCache.clear();
  }

  public void clearCache(Recipient recipient) {
    if (recipientCache.containsKey(recipient.getRecipientId()))
      recipientCache.remove(recipient.getRecipientId());
  }

  private RecipientDetails getRecipientDetails(Context context, String number) {
    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = context.getContentResolver().query(uri, CALLER_ID_PROJECTION,
                                                       null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        Uri contactUri      = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
        Bitmap contactPhoto = ContactPhotoFactory.getContactPhoto(context, Uri.withAppendedPath(Contacts.CONTENT_URI,
                                                                                                cursor.getLong(2)+""));
        return new RecipientDetails(cursor.getString(0), cursor.getString(3), contactUri, contactPhoto,
                                    BitmapUtil.getCircleBitmap(contactPhoto));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  private RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    try {
      GroupDatabase.GroupRecord record  = DatabaseFactory.getGroupDatabase(context)
                                                         .getGroup(GroupUtil.getDecodedId(groupId));

      if (record != null) {
        byte[] avatarBytes = record.getAvatar();
        Bitmap avatar;

        if (avatarBytes == null) avatar = ContactPhotoFactory.getDefaultGroupPhoto(context);
        else                     avatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);

        return new RecipientDetails(record.getTitle(), groupId, null, avatar, BitmapUtil.getCircleBitmap(avatar));
      }

      return null;
    } catch (IOException e) {
      Log.w("RecipientProvider", e);
      return null;
    }
  }

  public static class RecipientDetails {
    public final String name;
    public final String number;
    public final Bitmap avatar;
    public final Bitmap croppedAvatar;
    public final Uri    contactUri;

    public RecipientDetails(String name, String number, Uri contactUri, Bitmap avatar, Bitmap croppedAvatar) {
      this.name          = name;
      this.number        = number;
      this.avatar        = avatar;
      this.croppedAvatar = croppedAvatar;
      this.contactUri    = contactUri;
    }
  }

}