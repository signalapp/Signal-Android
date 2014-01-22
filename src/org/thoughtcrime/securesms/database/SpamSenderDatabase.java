/**
 * Copyright (C) 2014 Marek Wehmer
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
package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database for storage of spam sender numbers.
 *
 * @author Marek Wehmer
 */

public class SpamSenderDatabase extends Database {

  public  static final String TABLE_NAME         = "spam_sender";
  private static final String ID                 = "_id";
  public  static final String SENDER             = "sender";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " integer PRIMARY KEY, "                +
    SENDER + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS spam_sender_sender_index ON " + TABLE_NAME + " (" + SENDER + ");"
  };
  private static final String SENDER_WHERE = SENDER + " = ?";

  public SpamSenderDatabase(Context context, SQLiteOpenHelper databaseHelper) {
      super(context, databaseHelper);
  }

  public Cursor getSpamSenders() {
      SQLiteDatabase database = databaseHelper.getReadableDatabase();
      Cursor cursor           = database.query(TABLE_NAME, null, null, null, null, null, null);
      return cursor;
  }

  public void saveSpamSender(String sender)
  {
    SQLiteDatabase database   = databaseHelper.getWritableDatabase();

    ContentValues contentValues = new ContentValues();
    contentValues.put(SENDER, sender);

    database.replace(TABLE_NAME, null, contentValues);
  }

  public void deleteSpamSender(long id) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {id+""});
  }

  public boolean isSpamSender(String sender) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = db.rawQuery("SELECT COUNT(" + SENDER + ") FROM " + TABLE_NAME + " WHERE " + SENDER + " = ?", new String[] { sender });
    cursor.moveToFirst();
    boolean spamSender = cursor.getLong(0) > 0L;
    cursor.close();
    return spamSender;
  }

  public void deleteSpamSender(String spamNumber) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, SENDER_WHERE, new String[] { spamNumber });
  }
}
