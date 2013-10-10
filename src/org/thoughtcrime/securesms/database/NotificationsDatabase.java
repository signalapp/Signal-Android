/**
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
import android.net.Uri;
import android.provider.Settings;

public class NotificationsDatabase extends Database {

  public static final String DEFAULT_SOUND                   = Settings.System.DEFAULT_NOTIFICATION_URI.toString();

  public static final String DEFAULT_VIBRATE_PATTERN         = "default";
  public static final String DEFAULT_VIBRATE_PATTERN_CUSTOM  = "0,200,300,200";

  public static final String DEFAULT_LED_COLOR               = "green";
  public static final String DEFAULT_LED_PATTERN             = "500,2000";
  public static final String DEFAULT_LED_PATTERN_CUSTOM      = "500,2000";

  private static final Uri CHANGE_URI = Uri.parse("content://textsecure/notifications");

  public static final String TABLE_NAME    = "notifications";

  public static final String _ID                      = "_id";
  public static final String CONTACT_ID               = "contact_id";
  public static final String CONTACT_LOOKUPKEY        = "contact_lookupkey";
  public static final String CONTACT_NAME             = "contact_name";
  public static final String SUMMARY                  = "summary";
  public static final String ENABLED                  = "enabled";
  public static final String SOUND                    = "sound";
  public static final String VIBRATE                  = "vibrate";
  public static final String VIBRATE_PATTERN          = "vibrate_pattern";
  public static final String VIBRATE_PATTERN_CUSTOM   = "vibrate_pattern_custom";
  public static final String LED                      = "led";
  public static final String LED_COLOR                = "led_color";
  public static final String LED_PATTERN              = "led_pattern";
  public static final String LED_PATTERN_CUSTOM       = "led_pattern_custom";


  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("  +
      _ID                    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +

      CONTACT_ID             + " integer, " +
      CONTACT_LOOKUPKEY      + " text, " +
      CONTACT_NAME           + " text default 'Unknown', " +
      SUMMARY                + " text default 'Default notifications', " +

      ENABLED                + " integer default 1, " +
      SOUND                  + " text default '" + DEFAULT_SOUND + "', " +
      VIBRATE                + " integer default 1, " +
      VIBRATE_PATTERN        + " text default '" + DEFAULT_VIBRATE_PATTERN + "', " +
      VIBRATE_PATTERN_CUSTOM + " text default '" + DEFAULT_VIBRATE_PATTERN_CUSTOM + "', " +
      LED                    + " integer default 1, " +
      LED_COLOR              + " text default '" + DEFAULT_LED_COLOR + "', " +
      LED_PATTERN            + " text default '" + DEFAULT_LED_PATTERN + "', " +
      LED_PATTERN_CUSTOM     + " text default '" + DEFAULT_LED_PATTERN_CUSTOM + "', " +
      "UNIQUE (" + CONTACT_LOOKUPKEY + ") ON CONFLICT IGNORE" +
      ");";

  public NotificationsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getDefaultNotification()   {
    return getNotification("0");
  }

  public Cursor getNotification(String rowId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    String selection = _ID + " = ? ";
    String[] selectionArgs = new String[]{rowId+""};

    Cursor cursor = database.query(TABLE_NAME, null, selection, selectionArgs, null, null, null);

    if (cursor == null || cursor.getCount() != 1)
      return null;

    cursor.setNotificationUri(context.getContentResolver(), CHANGE_URI);
    cursor.moveToFirst();

    return cursor;
  }

  public Cursor getNotification(String notificationKey, String contactId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    String selection = CONTACT_ID + " = ? OR " + CONTACT_LOOKUPKEY + " = ? ";
    String[] selectionArgs = new String[]{contactId+"", notificationKey+""};

    Cursor cursor = database.query(TABLE_NAME, null, selection, selectionArgs, null, null, null);

    if (cursor == null || cursor.getCount() != 1)
      return null;

    cursor.setNotificationUri(context.getContentResolver(), CHANGE_URI);
    cursor.moveToFirst();

    return cursor;
  }

  public Cursor getNotifications() {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    String selection = _ID + " > 0";

    Cursor cursor = database.query(TABLE_NAME, null, selection, null, null, null, CONTACT_NAME);

    if (cursor == null || cursor.getCount() < 1)
    {
      return null;
    }

    cursor.setNotificationUri(context.getContentResolver(), CHANGE_URI);

    return cursor;
  }

  public long insertNotification(ContentValues values) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    return db.insertOrThrow(TABLE_NAME, null, values);
  }

  public int updateNotification(long rowId, ContentValues values) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();

    final String contactSelection = _ID + " = ?";
    final String[] contactSelectionArgs = { rowId+"" };

    return db.update(TABLE_NAME, values, contactSelection, contactSelectionArgs);
  }

  public void saveNotification(ContentValues values)
  {
    SQLiteDatabase database   = databaseHelper.getWritableDatabase();

    database.replace(TABLE_NAME, null, values);

    context.getContentResolver().notifyChange(CHANGE_URI, null);
  }

  public int deleteNotification(String rowId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    String selection = _ID + " = ? ";
    String[] selectionArgs = new String[]{rowId+""};

    int count = database.delete(TABLE_NAME, selection, selectionArgs);

    context.getContentResolver().notifyChange(CHANGE_URI, null);
    return count;
  }


  public boolean updateNotification(long rowId, String column, Object newValue)
  {
    ContentValues vals = new ContentValues();
    if (newValue.getClass().equals(Boolean.class)) {
      vals.put(column, (Boolean) newValue);
    } else {
      vals.put(column, String.valueOf(newValue));
    }

    int rows = updateNotification(rowId, vals);

    return (rows == 1);
  }

}
