/**
 * Copyright (C) 2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.LRUCache;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public class ContactPhotoFactory {

  private static final Object defaultPhotoLock      = new Object();
  private static final Object defaultGroupPhotoLock = new Object();

  private static Bitmap defaultContactPhoto;
  private static Bitmap defaultGroupContactPhoto;

  private static final Map<Uri,Bitmap> localUserContactPhotoCache =
      Collections.synchronizedMap(new LRUCache<Uri,Bitmap>(2));

  private static final String[] CONTENT_URI_PROJECTION = new String[] {
    ContactsContract.Contacts._ID,
    ContactsContract.Contacts.DISPLAY_NAME,
    ContactsContract.Contacts.LOOKUP_KEY
  };

  public static Bitmap getDefaultContactPhoto(Context context) {
    synchronized (defaultPhotoLock) {
      if (defaultContactPhoto == null)
        defaultContactPhoto =  BitmapFactory.decodeResource(context.getResources(),
                                                            R.drawable.ic_contact_picture);
      return defaultContactPhoto;
    }
  }

  public static Bitmap getDefaultGroupPhoto(Context context) {
    synchronized (defaultGroupPhotoLock) {
      if (defaultGroupContactPhoto == null)
        defaultGroupContactPhoto =  BitmapFactory.decodeResource(context.getResources(),
                                                                 R.drawable.ic_group_photo);
      return defaultGroupContactPhoto;
    }
  }

  public static Bitmap getLocalUserContactPhoto(Context context, Uri uri) {
    if (uri == null) return getDefaultContactPhoto(context);

    Bitmap contactPhoto = localUserContactPhotoCache.get(uri);

    if (contactPhoto == null) {
      Cursor cursor = context.getContentResolver().query(uri, CONTENT_URI_PROJECTION,
                                                         null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        contactPhoto = getContactPhoto(context, Uri.withAppendedPath(Contacts.CONTENT_URI,
                                       cursor.getLong(0) + ""));
      } else {
        contactPhoto = getDefaultContactPhoto(context);
      }

      localUserContactPhotoCache.put(uri, contactPhoto);
    }

    return contactPhoto;
  }

  public static void clearCache() {
    localUserContactPhotoCache.clear();
  }

  public static void clearCache(Recipient recipient) {
    if (localUserContactPhotoCache.containsKey(recipient.getContactUri()))
    localUserContactPhotoCache.remove(recipient.getContactUri());
  }

  public static Bitmap getContactPhoto(Context context, Uri uri) {
    InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);

    final Bitmap contactPhoto;
    if (inputStream == null) contactPhoto = ContactPhotoFactory.getDefaultContactPhoto(context);
    else                     contactPhoto = BitmapFactory.decodeStream(inputStream);

    return contactPhoto;
  }
}
