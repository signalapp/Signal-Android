package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.util.List;

public class PushDatabase extends Database {

  private static final String TABLE_NAME   = "push";
  public  static final String ID           = "_id";
  public  static final String TYPE         = "type";
  public  static final String SOURCE       = "source";
  public  static final String DEVICE_ID    = "device_id";
  public  static final String BODY         = "body";
  public  static final String TIMESTAMP    = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      TYPE + " INTEGER, " + SOURCE + " TEXT, " + DEVICE_ID + " INTEGER, " + BODY + " TEXT, " + TIMESTAMP + " INTEGER);";

  public PushDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public long insert(IncomingPushMessage message) {
    ContentValues values = new ContentValues();
    values.put(TYPE, message.getType());
    values.put(SOURCE, message.getSource());
    values.put(DEVICE_ID, message.getSourceDevice());
    values.put(BODY, Base64.encodeBytes(message.getBody()));
    values.put(TIMESTAMP, message.getTimestampMillis());

    return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  public Cursor getPending() {
    return databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
  }

  public void delete(long id) {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID_WHERE, new String[] {id+""});
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static class Reader {
    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public IncomingPushMessage getNext() {
      try {
        if (cursor == null || !cursor.moveToNext())
          return null;

        int          type         = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        String       source       = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE));
        int          deviceId     = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
        byte[]       body         = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow(BODY)));
        long         timestamp    = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));

        return new IncomingPushMessage(type, source, deviceId, body, timestamp);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public long getCurrentId() {
      return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
    }

    public void close() {
      this.cursor.close();
    }
  }

}
