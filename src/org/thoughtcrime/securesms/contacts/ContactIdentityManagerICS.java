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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;

import java.util.LinkedList;
import java.util.List;

class ContactIdentityManagerICS extends ContactIdentityManager {

  public ContactIdentityManagerICS(Context context) {
    super(context);
  }

  @SuppressLint("NewApi")
  @Override
  public Uri getSelfIdentityUri() {
    String[] PROJECTION = new String[] {
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.LOOKUP_KEY,
        PhoneLookup._ID,
    };

    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(ContactsContract.Profile.CONTENT_URI,
                                                    PROJECTION, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  @Override
  public boolean isSelfIdentityAutoDetected() {
    return true;
  }

  @SuppressLint("NewApi")
  @Override
  public List<Long> getSelfIdentityRawContactIds() {
    List<Long> results = new LinkedList<Long>();

    String[] PROJECTION = new String[] {
        ContactsContract.Profile._ID
    };

    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI,
                                                  PROJECTION, null, null, null);

      if (cursor == null || cursor.getCount() == 0)
        return null;

      while (cursor.moveToNext()) {
        results.add(cursor.getLong(0));
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }
}
