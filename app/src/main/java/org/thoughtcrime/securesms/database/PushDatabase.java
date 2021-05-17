package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import org.session.libsignal.utilities.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.session.libsignal.utilities.Base64;
import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.messages.SignalServiceEnvelope;
import org.session.libsignal.utilities.Util;

import java.io.IOException;

public class PushDatabase extends Database {

  private static final String TAG = PushDatabase.class.getSimpleName();

  public static final String TABLE_NAME       = "push";
  public static final String ID               = "_id";
  public static final String TYPE             = "type";
  public static final String SOURCE           = "source";
  public static final String DEVICE_ID        = "device_id";
  public static final String LEGACY_MSG       = "body";
  public static final String CONTENT          = "content";
  public static final String TIMESTAMP        = "timestamp";
  public static final String SERVER_TIMESTAMP = "server_timestamp";
  public static final String SERVER_GUID      = "server_guid";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      TYPE + " INTEGER, " + SOURCE + " TEXT, " + DEVICE_ID + " INTEGER, " + LEGACY_MSG + " TEXT, " + CONTENT + " TEXT, " + TIMESTAMP + " INTEGER, " +
      SERVER_TIMESTAMP + " INTEGER DEFAULT 0, " + SERVER_GUID + " TEXT DEFAULT NULL);";

  public PushDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public long insert(@NonNull SignalServiceEnvelope envelope) {
    Optional<Long> messageId = find(envelope);

    if (messageId.isPresent()) {
      return messageId.get();
    } else {
      ContentValues values = new ContentValues();
      values.put(TYPE, envelope.getType());
      values.put(SOURCE, envelope.getSource());
      values.put(DEVICE_ID, envelope.getSourceDevice());
      values.put(LEGACY_MSG, "");
      values.put(CONTENT, envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "");
      values.put(TIMESTAMP, envelope.getTimestamp());
      values.put(SERVER_TIMESTAMP, envelope.getServerTimestamp());
      values.put(SERVER_GUID, "");

      return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
    }
  }

  public SignalServiceEnvelope get(long id) throws NoSuchMessageException {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, ID_WHERE,
                                                          new String[] {String.valueOf(id)},
                                                          null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        String content       = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));

        return new SignalServiceEnvelope(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
                                         cursor.getString(cursor.getColumnIndexOrThrow(SOURCE)),
                                         cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID)),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)),
                                         Util.isEmpty(content) ? null : Base64.decode(content),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_TIMESTAMP)));
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NoSuchMessageException(e);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    throw new NoSuchMessageException("Not found");
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

  private Optional<Long> find(SignalServiceEnvelope envelope) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, TYPE + " = ? AND " + SOURCE + " = ? AND " +
                                                DEVICE_ID + " = ? AND " + LEGACY_MSG + " = ? AND " +
                                                CONTENT + " = ? AND " + TIMESTAMP + " = ?" ,
                              new String[] {String.valueOf(envelope.getType()),
                                            envelope.getSource(),
                                            String.valueOf(envelope.getSourceDevice()),
                                            "",
                                            envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "",
                                            String.valueOf(envelope.getTimestamp())},
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Optional.of(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
      } else {
        return Optional.absent();
      }
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public static class Reader {
    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public SignalServiceEnvelope getNext() {
      try {
        if (cursor == null || !cursor.moveToNext())
          return null;

        int    type            = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        String source          = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE));
        int    deviceId        = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
        String content         = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));
        long   timestamp       = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
        long   serverTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_TIMESTAMP));

        return new SignalServiceEnvelope(type, source, deviceId, timestamp,
                                         content != null ? Base64.decode(content) : null,
                                         serverTimestamp);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public void close() {
      this.cursor.close();
    }
  }
}
