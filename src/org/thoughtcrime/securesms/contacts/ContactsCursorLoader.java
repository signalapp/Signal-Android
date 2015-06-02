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
import android.util.Log;

import junit.framework.Assert;

import java.util.concurrent.Semaphore;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {
  private static final String TAG        = ContactsCursorLoader.class.getSimpleName();
  private static final int    DB_PERMITS = 100;

  private final Context          context;
  private final String           filter;
  private final boolean          pushOnly;
  private final Semaphore        dbSemaphore = new Semaphore(DB_PERMITS);
  private       ContactsDatabase db;

  public ContactsCursorLoader(Context context, String filter, boolean pushOnly) {
    super(context);
    this.context  = context;
    this.filter   = filter;
    this.pushOnly = pushOnly;
    this.db       = new ContactsDatabase(context);
  }

  @Override
  public Cursor loadInBackground() {
    try {
      dbSemaphore.acquire();
      return db.query(filter, pushOnly);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    } finally {
      dbSemaphore.release();
    }
  }

  @Override
  public void onReset() {
    Log.w(TAG, "onReset()");
    try {
      dbSemaphore.acquire(DB_PERMITS);
      db.close();
      db = new ContactsDatabase(context);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    } finally {
      dbSemaphore.release(DB_PERMITS);
    }
    super.onReset();
  }
}
