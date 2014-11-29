/**
 * Copyright (C) 2014 Whisper Systems
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
import android.util.Log;

import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Database storing per-recipient notification settings. Please note that this database does
 * not store Recipient ID <=> settings, but rather Recipient Number <=> Settings, so that
 * Groups and simple recipients can be handled the same way.
 *
 * @author Lukas Barth
 */
public class RecipientNotificationsDatabase extends Database {

  private static final String TAG = RecipientNotificationsDatabase.class.getSimpleName();

  private static final String TABLE_NAME        = "notifications";
  public  static final String ID                = "_id";
  public  static final String RECIPIENT_NR      = "recipient_nr";
  public  static final String SILENCE_UNTIL     = "silence_until";
  public  static final String SILENCE_PERMANENT = "silence_permanent";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
                                            RECIPIENT_NR + " TEXT UNIQUE, " + SILENCE_UNTIL + " INTEGER, " + SILENCE_PERMANENT + " BOOLEAN NOT NULL);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS notifications_recipient_index ON " + TABLE_NAME + " (" + RECIPIENT_NR + ");",
  };

  public RecipientNotificationsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public boolean isSilencedPermanently(Recipient recipient) {
    SQLiteDatabase db  = databaseHelper.getWritableDatabase();
    Cursor cursor      = null;

    try {
      cursor = db.query(TABLE_NAME, null, RECIPIENT_NR + " = ?", new String[] {recipient.getNumber()}, null, null, null);

      if (cursor.isAfterLast()) {
        return false;
      }

      cursor.moveToFirst();
      Boolean isPermanentlySilenced = (cursor.getInt(cursor.getColumnIndexOrThrow(SILENCE_PERMANENT)) > 0);

      return isPermanentlySilenced;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public Long getSilencedUntil(Recipient recipient) {
    SQLiteDatabase db  = databaseHelper.getWritableDatabase();
    Cursor cursor      = null;

    try {
      cursor = db.query(TABLE_NAME, null, RECIPIENT_NR + " = ?", new String[] {recipient.getNumber()}, null, null, null);

      if (cursor.isAfterLast()) {
        return null;
      }

      cursor.moveToFirst();
      Long silencedUntil = cursor.getLong(cursor.getColumnIndexOrThrow(SILENCE_UNTIL));

      return silencedUntil;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private boolean isInDatabase(Recipient recipient) {
    SQLiteDatabase db  = databaseHelper.getWritableDatabase();
    Cursor cursor      = null;

    try {
      cursor = db.query(TABLE_NAME, null, RECIPIENT_NR + " = ?", new String[] {recipient.getNumber()}, null, null, null);

      if (cursor.isAfterLast()) {
        return false;
      }

      return true;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean isSilencedNow(Recipient recipient) {
    SQLiteDatabase db  = databaseHelper.getWritableDatabase();
    Cursor cursor      = null;

    try {
      cursor = db.query(TABLE_NAME, null, RECIPIENT_NR + " = ?", new String[] {recipient.getNumber()}, null, null, null);

      if (cursor.isAfterLast()) {
        return false;
      }

      cursor.moveToFirst();

      Boolean isPermanentlySilenced = cursor.getInt(cursor.getColumnIndexOrThrow(SILENCE_PERMANENT)) > 0;
      if (isPermanentlySilenced)
        return true;

      Long silencedUntil = cursor.getLong(cursor.getColumnIndexOrThrow(SILENCE_UNTIL));
      Long nowTimestamp = System.currentTimeMillis() / 1000;
      if ((silencedUntil != null) && (nowTimestamp <= silencedUntil))
        return true;

      return false;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private void updateRecipient(Recipient recipient, boolean silencePermanent, Long silenceUntil) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    ContentValues values = new ContentValues(3);
    values.put(RECIPIENT_NR, recipient.getNumber());
    values.put(SILENCE_PERMANENT, silencePermanent);
    values.put(SILENCE_UNTIL, silenceUntil);

    if (!isInDatabase(recipient)) {
      Log.d(TAG, "Inserting " + recipient.getName() + " into notifications db");
      db.insert(TABLE_NAME, null, values);
    } else {
      Log.d(TAG, "Updating " + recipient.getName() + " in notifications db");
      db.update(TABLE_NAME, values, RECIPIENT_NR + " = ?", new String[] {recipient.getNumber()});
    }
  }

  public void setSilenceUntil(Recipient recipient, Long silenceUntil) {
    this.updateRecipient(recipient, this.isSilencedPermanently(recipient), silenceUntil);
  }

  public void setSilencePermanently(Recipient recipient, boolean silencePermanently) {
    if (silencePermanently) {
      this.updateRecipient(recipient, silencePermanently, null);
    } else {
      this.updateRecipient(recipient, silencePermanently, this.getSilencedUntil(recipient));
    }
  }
}
