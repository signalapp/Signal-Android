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
import android.database.MergeCursor;
import android.support.v4.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();

  public final static int MODE_ALL        = 0;
  public final static int MODE_PUSH_ONLY  = 1;
  public final static int MODE_OTHER_ONLY = 2;

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

    if (mode != MODE_OTHER_ONLY) {
      cursorList.add(contactsDatabase.queryTextSecureContacts(filter));
    }

    if (mode == MODE_ALL) {
      cursorList.add(contactsDatabase.querySystemContacts(filter));
    } else if (mode == MODE_OTHER_ONLY) {
      cursorList.add(contactsDatabase.queryNonTextSecureContacts(filter));
    }

    if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
      cursorList.add(contactsDatabase.getNewNumberCursor(filter));
    }

    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }
}
