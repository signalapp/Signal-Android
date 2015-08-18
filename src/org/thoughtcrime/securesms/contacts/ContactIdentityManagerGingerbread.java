package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.List;

class ContactIdentityManagerGingerbread extends ContactIdentityManager {

  public ContactIdentityManagerGingerbread(Context context) {
    super(context);
  }

  @Override
  public Uri getSelfIdentityUri() {
    String contactUriString = TextSecurePreferences.getIdentityContactUri(context);

    if      (hasLocalNumber())         return getContactUriForNumber(getLocalNumber());
    else if (contactUriString != null) return Uri.parse(contactUriString);

    return null;
  }

  @Override
  public boolean isSelfIdentityAutoDetected() {
    return hasLocalNumber() && getContactUriForNumber(getLocalNumber()) != null;
  }

  private Uri getContactUriForNumber(String number) {
    String[] PROJECTION = new String[] {
      PhoneLookup.DISPLAY_NAME,
      PhoneLookup.LOOKUP_KEY,
      PhoneLookup._ID,
    };

    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(uri, PROJECTION, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  private long getSelfIdentityContactId() {
    Uri contactUri = getSelfIdentityUri();

    if (contactUri == null)
      return -1;

    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(contactUri,
                                                  new String[] {ContactsContract.Contacts._ID},
                                                  null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      } else {
        return -1;
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private String getLocalNumber() {
    return ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE))
             .getLine1Number();
  }

  private boolean hasLocalNumber() {
    String number = getLocalNumber();
    return (number != null) && (number.trim().length() > 0);
  }



}
