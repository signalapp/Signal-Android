package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;

public class PushDatabase extends Database {

  private static final String TAG = PushDatabase.class.getSimpleName();

  private static final String TABLE_NAME   = "push";
  public  static final String ID           = "_id";
  public  static final String TYPE         = "type";
  public  static final String SOURCE       = "source";
  public  static final String DEVICE_ID    = "device_id";
  public  static final String LEGACY_MSG   = "body";
  public  static final String CONTENT      = "content";
  public  static final String TIMESTAMP    = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
      TYPE + " INTEGER, " + SOURCE + " TEXT, " + DEVICE_ID + " INTEGER, " + LEGACY_MSG + " TEXT, " + CONTENT + " TEXT, " + TIMESTAMP + " INTEGER);";

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
      values.put(LEGACY_MSG, envelope.hasLegacyMessage() ? Base64.encodeBytes(envelope.getLegacyMessage()) : "");
      values.put(CONTENT, envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "");
      values.put(TIMESTAMP, envelope.getTimestamp());

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
        String legacyMessage = cursor.getString(cursor.getColumnIndexOrThrow(LEGACY_MSG));
        String content       = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));

        return new SignalServiceEnvelope(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
                                         cursor.getString(cursor.getColumnIndexOrThrow(SOURCE)),
                                         cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID)),
                                         "",
                                         cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)),
                                         Util.isEmpty(legacyMessage) ? null : Base64.decode(legacyMessage),
                                         Util.isEmpty(content) ? null : Base64.decode(content));
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
                                            envelope.hasLegacyMessage() ? Base64.encodeBytes(envelope.getLegacyMessage()) : "",
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

        int    type          = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        String source        = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE));
        int    deviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
        String legacyMessage = cursor.getString(cursor.getColumnIndexOrThrow(LEGACY_MSG));
        String content       = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));
        long   timestamp     = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));

        return new SignalServiceEnvelope(type, source, deviceId, "", timestamp,
                                         legacyMessage != null ? Base64.decode(legacyMessage) : null,
                                         content != null ? Base64.decode(content) : null);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public void close() {
      this.cursor.close();
    }
  }
}
