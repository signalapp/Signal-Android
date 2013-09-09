package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

import org.spongycastle.util.encoders.Base64;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Util;

public class PushDatabase extends Database {

  private static final String TABLE_NAME   = "push";
  public  static final String ID           = "_id";
  public  static final String TYPE         = "type";
  public  static final String SOURCE       = "source";
  public  static final String DESTINATIONS = "destinations";
  public  static final String BODY         = "body";
  public  static final String TIMESTAMP    = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      TYPE + " INTEGER, " + SOURCE + " TEXT, " + DESTINATIONS + " TEXT, " + BODY + " TEXT, " + TIMESTAMP + " INTEGER);";

  public PushDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public long insert(IncomingPushMessage message) {
    ContentValues values = new ContentValues();
    values.put(TYPE, message.getType());
    values.put(SOURCE, message.getSource());
    values.put(DESTINATIONS, Util.join(message.getDestinations(), ","));
    values.put(BODY, Base64.encode(message.getBody()));
    values.put(TIMESTAMP, message.getTimestampMillis());

    return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  public void delete(long id) {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID_WHERE, new String[] {id+""});
  }
}
