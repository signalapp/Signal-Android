/**
 * Copyright (C) 2013 Open Whisper Systems
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
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DirectoryHelper.UserCapabilities.Capability;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();

  public final static int MODE_ALL       = 0;
  public final static int MODE_PUSH_ONLY = 1;
  public final static int MODE_SMS_ONLY  = 2;

  private final String filter;
  private final int    mode;

  public ContactsCursorLoader(Context context, int mode, String filter) {
    super(context);

    this.filter = filter;
    this.mode   = mode;
  }

  @Override
  public Cursor loadInBackground() {
    ContactsDatabase  contactsDatabase = DatabaseFactory.getContactsDatabase(getContext());
    ArrayList<Cursor> cursorList       = new ArrayList<>(3);

    if (mode != MODE_SMS_ONLY) {
      cursorList.add(contactsDatabase.queryTextSecureContacts(filter));
    }

    if (mode == MODE_ALL) {
      cursorList.add(contactsDatabase.querySystemContacts(filter));
    } else if (mode == MODE_SMS_ONLY) {
      cursorList.add(filterNonPushContacts(contactsDatabase.querySystemContacts(filter)));
    }

    if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
      MatrixCursor newNumberCursor = new MatrixCursor(new String[] {ContactsDatabase.ID_COLUMN,
                                                                    ContactsDatabase.NAME_COLUMN,
                                                                    ContactsDatabase.NUMBER_COLUMN,
                                                                    ContactsDatabase.NUMBER_TYPE_COLUMN,
                                                                    ContactsDatabase.LABEL_COLUMN,
                                                                    ContactsDatabase.CONTACT_TYPE_COLUMN}, 1);

      newNumberCursor.addRow(new Object[] {-1L, getContext().getString(R.string.contact_selection_list__unknown_contact),
                                           filter, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                           "\u21e2", ContactsDatabase.NEW_TYPE});

      cursorList.add(newNumberCursor);
    }

    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }

  private @NonNull Cursor filterNonPushContacts(@NonNull Cursor cursor) {
    try {
      final long startMillis = System.currentTimeMillis();
      final MatrixCursor matrix = new MatrixCursor(new String[]{ContactsDatabase.ID_COLUMN,
                                                                ContactsDatabase.NAME_COLUMN,
                                                                ContactsDatabase.NUMBER_COLUMN,
                                                                ContactsDatabase.NUMBER_TYPE_COLUMN,
                                                                ContactsDatabase.LABEL_COLUMN,
                                                                ContactsDatabase.CONTACT_TYPE_COLUMN});
      while (cursor.moveToNext()) {
        final String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN));
        final Recipients recipients = RecipientFactory.getRecipientsFromString(getContext(), number, true);

        if (DirectoryHelper.getUserCapabilities(getContext(), recipients)
                           .getTextCapability() != Capability.SUPPORTED)
        {
          matrix.addRow(new Object[]{cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.ID_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN)),
                                     number,
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_TYPE_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.LABEL_COLUMN)),
                                     ContactsDatabase.NORMAL_TYPE});
        }
      }
      Log.w(TAG, "filterNonPushContacts() -> " + (System.currentTimeMillis() - startMillis) + "ms");
      return matrix;
    } finally {
      cursor.close();
    }
  }
}
