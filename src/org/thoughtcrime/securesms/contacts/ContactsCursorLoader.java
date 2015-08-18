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

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private final String  filter;
  private       boolean includeSmsContacts;

  public ContactsCursorLoader(Context context, boolean includeSmsContacts, String filter) {
    super(context);

    this.filter   = filter;
    this.includeSmsContacts = includeSmsContacts;
  }

  @Override
  public Cursor loadInBackground() {
    ContactsDatabase  contactsDatabase = DatabaseFactory.getContactsDatabase(getContext());
    ArrayList<Cursor> cursorList       = new ArrayList<>(3);

    cursorList.add(contactsDatabase.queryTextSecureContacts(filter));

    if (includeSmsContacts) {
      cursorList.add(contactsDatabase.querySystemContacts(filter));
    }

    if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
      cursorList.add(contactsDatabase.getNewNumberCursor(filter));
    }

    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }
}
