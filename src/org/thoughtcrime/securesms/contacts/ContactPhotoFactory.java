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

  private static final Object defaultPhotoLock              = new Object();
  private static final Object defaultGroupPhotoLock         = new Object();
  private static final Object defaultPhotoCroppedLock       = new Object();
  private static final Object defaultGroupPhotoCroppedLock  = new Object();

  private static Bitmap defaultContactPhoto;
  private static Bitmap defaultGroupContactPhoto;
  private static Bitmap defaultContactPhotoCropped;
  private static Bitmap defaultGroupContactPhotoCropped;

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

  public static Bitmap getDefaultContactPhotoCropped(Context context) {
    synchronized (defaultPhotoCroppedLock) {
      if (defaultContactPhotoCropped == null)
        defaultContactPhotoCropped = BitmapUtil.getCircleBitmap(getDefaultContactPhoto(context));

      return defaultContactPhotoCropped;
    }
  }

  public static Bitmap getDefaultGroupPhotoCropped(Context context) {
    synchronized (defaultGroupPhotoCroppedLock) {
      if (defaultGroupContactPhotoCropped == null)
        defaultGroupContactPhotoCropped = BitmapUtil.getCircleBitmap(getDefaultGroupPhoto(context));

      return defaultGroupContactPhotoCropped;
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
