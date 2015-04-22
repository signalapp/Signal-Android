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
import android.support.v4.content.CursorLoader;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {
  private final static Object lock = new Object();

  private final Context          context;
  private final String           filter;
  private final boolean          pushOnly;
  private       ContactsDatabase db;

  public ContactsCursorLoader(Context context, String filter, boolean pushOnly) {
    super(context);
    this.context  = context;
    this.filter   = filter;
    this.pushOnly = pushOnly;
  }

  @Override
  public Cursor loadInBackground() {
    synchronized (lock) {
      db = ContactsDatabase.getInstance(context);
      Cursor csr = db.query(filter, pushOnly);
      ContactsDatabase.destroyInstance();
      return csr;
    }
  }

}
