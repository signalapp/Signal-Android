package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;

import org.thoughtcrime.securesms.R;

import java.io.InputStream;

public class ContactPhotoFactory {

  private static final Object defaultPhotoLock = new Object();
  private static final Object localUserLock    = new Object();

  private static Bitmap defaultContactPhoto;
  private static Bitmap localUserContactPhoto;

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
    }

    return defaultContactPhoto;
  }

  public static Bitmap getLocalUserContactPhoto(Context context, Uri uri) {
    synchronized (localUserLock) {
      if (localUserContactPhoto == null) {
        Cursor cursor = context.getContentResolver().query(uri, CONTENT_URI_PROJECTION,
                                                           null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
          localUserContactPhoto = getContactPhoto(context, Uri.withAppendedPath(Contacts.CONTENT_URI,
                                                                                cursor.getLong(0) + ""));
        } else {
          localUserContactPhoto = getDefaultContactPhoto(context);
        }
      }
    }

    return localUserContactPhoto;
  }

  public static void clearCache() {
    synchronized (localUserLock) {
      localUserContactPhoto = null;
    }
  }

  private static Bitmap getContactPhoto(Context context, Uri uri) {
    InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);

    if (inputStream == null) return ContactPhotoFactory.getDefaultContactPhoto(context);
    else                     return BitmapFactory.decodeStream(inputStream);
  }
}
