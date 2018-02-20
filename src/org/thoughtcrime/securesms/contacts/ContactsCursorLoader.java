/*
 * Copyright (C) 2013-2017 Open Whisper Systems
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

import android.Manifest;
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
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();

  public static final int MODE_ALL       = 0;
  public static final int MODE_PUSH_ONLY = 1;
  public static final int MODE_SMS_ONLY  = 2;

  private static final String[] CONTACT_PROJECTION = new String[]{ContactsDatabase.NAME_COLUMN,
                                                                  ContactsDatabase.NUMBER_COLUMN,
                                                                  ContactsDatabase.NUMBER_TYPE_COLUMN,
                                                                  ContactsDatabase.LABEL_COLUMN,
                                                                  ContactsDatabase.CONTACT_TYPE_COLUMN};


  private final String       filter;
  private final int          mode;
  private final boolean      recents;
  private final int          recentContactsLimit;

  public ContactsCursorLoader(@NonNull Context context, int mode, String filter, boolean recents,
                              int recentContactsLimit)
  {
    super(context);

    this.filter              = filter;
    this.mode                = mode;
    this.recents             = recents;
    this.recentContactsLimit = recentContactsLimit;
  }

  @Override
  public Cursor loadInBackground() {
    ContactsDatabase  contactsDatabase = DatabaseFactory.getContactsDatabase(getContext());
    ThreadDatabase    threadDatabase   = DatabaseFactory.getThreadDatabase(getContext());
    ArrayList<Cursor> cursorList       = new ArrayList<>(4);

    if (recents && TextUtils.isEmpty(filter)) {
      try (Cursor recentConversations = DatabaseFactory.getThreadDatabase(
              getContext()).getRecentConversationList(recentContactsLimit + 1)) {
        MatrixCursor          synthesizedContacts = new MatrixCursor(CONTACT_PROJECTION);
        synthesizedContacts.addRow(new Object[] {getContext().getString(R.string.ContactsCursorLoader_recent_chats), "",
                                                 ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                 "", ContactsDatabase.DIVIDER_TYPE});

        ThreadDatabase.Reader reader = threadDatabase.readerFor(recentConversations);

        ThreadRecord threadRecord;

        int count = 0;
        while ((threadRecord = reader.getNext()) != null) {
          count++;
          if (count <= recentContactsLimit) {
            synthesizedContacts.addRow(new Object[] {threadRecord.getRecipient().toShortString(),
                                                     threadRecord.getRecipient().getAddress().serialize(),
                                                     ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                     "", ContactsDatabase.RECENT_TYPE});
          }
        }

        if (count > recentContactsLimit) {
          synthesizedContacts.addRow(new Object[]{getContext().getString(R.string.ContactsCursorLoader_more), "",
                                                  ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                  "", ContactsDatabase.MORE_TYPE});
        }

        synthesizedContacts.addRow(new Object[] {getContext().getString(R.string.ContactsCursorLoader_contacts), "",
                                                 ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                 "", ContactsDatabase.DIVIDER_TYPE});
        if (synthesizedContacts.getCount() > 2) cursorList.add(synthesizedContacts);
      }
    }

    if (Permissions.hasAny(getContext(), Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      if (mode != MODE_SMS_ONLY) {
        cursorList.add(contactsDatabase.queryTextSecureContacts(filter));
      }

      if (mode == MODE_ALL) {
        cursorList.add(contactsDatabase.querySystemContacts(filter));
      } else if (mode == MODE_SMS_ONLY) {
        cursorList.add(filterNonPushContacts(contactsDatabase.querySystemContacts(filter)));
      }
    }

    if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
      MatrixCursor newNumberCursor = new MatrixCursor(CONTACT_PROJECTION, 1);

      newNumberCursor.addRow(new Object[] {getContext().getString(R.string.contact_selection_list__unknown_contact),
                                           filter, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                           "\u21e2", ContactsDatabase.NEW_TYPE});

      cursorList.add(newNumberCursor);
    }

    if (cursorList.size() > 0) return new MergeCursor(cursorList.toArray(new Cursor[0]));
    else                       return null;
  }

  private @NonNull Cursor filterNonPushContacts(@NonNull Cursor cursor) {
    try {
      final long startMillis = System.currentTimeMillis();
      final MatrixCursor matrix = new MatrixCursor(CONTACT_PROJECTION);
      while (cursor.moveToNext()) {
        final String    number    = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN));
        final Recipient recipient = Recipient.from(getContext(), Address.fromExternal(getContext(), number), false);

        if (recipient.resolve().getRegistered() != RecipientDatabase.RegisteredState.REGISTERED) {
          matrix.addRow(new Object[]{cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN)),
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
