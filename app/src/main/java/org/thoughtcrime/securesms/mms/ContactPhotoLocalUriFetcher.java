package org.thoughtcrime.securesms.mms;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import java.io.FileNotFoundException;
import java.io.InputStream;

class ContactPhotoLocalUriFetcher extends StreamLocalUriFetcher {

  private static final String TAG = ContactPhotoLocalUriFetcher.class.getSimpleName();

  ContactPhotoLocalUriFetcher(Context context, Uri uri) {
    super(context.getContentResolver(), uri);
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver)
      throws FileNotFoundException
  {
    if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
      return ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true);
    } else {
      return ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri);
    }
  }
}
