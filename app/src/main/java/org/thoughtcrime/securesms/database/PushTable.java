package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

public class PushTable extends DatabaseTable {

  private static final String TAG = Log.tag(PushTable.class);

  private static final String TABLE_NAME                 = "push";
  public  static final String ID                         = "_id";
  public  static final String TYPE                       = "type";
  public  static final String SOURCE_E164                = "source";
  public  static final String SOURCE_UUID                = "source_uuid";
  public  static final String DEVICE_ID                  = "device_id";
  public  static final String LEGACY_MSG                 = "body";
  public  static final String CONTENT                    = "content";
  public  static final String TIMESTAMP                  = "timestamp";
  public  static final String SERVER_RECEIVED_TIMESTAMP  = "server_timestamp";
  public  static final String SERVER_DELIVERED_TIMESTAMP = "server_delivered_timestamp";
  public  static final String SERVER_GUID                = "server_guid";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                         + " INTEGER PRIMARY KEY, " +
                                                                                  TYPE                       + " INTEGER, " +
                                                                                  SOURCE_E164                + " TEXT, " +
                                                                                  SOURCE_UUID                + " TEXT, " +
                                                                                  DEVICE_ID                  + " INTEGER, " +
                                                                                  LEGACY_MSG                 + " TEXT, " +
                                                                                  CONTENT                    + " TEXT, " +
                                                                                  TIMESTAMP                  + " INTEGER, " +
                                                                                  SERVER_RECEIVED_TIMESTAMP  + " INTEGER DEFAULT 0, " +
                                                                                  SERVER_DELIVERED_TIMESTAMP + " INTEGER DEFAULT 0, " +
                                                                                  SERVER_GUID                + " TEXT DEFAULT NULL);";

  public PushTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public long insert(@NonNull SignalServiceEnvelope envelope) {
    Optional<Long> messageId = find(envelope);

    if (messageId.isPresent()) {
      return -1;
    } else {
      ContentValues values = new ContentValues();
      values.put(TYPE, envelope.getType());
      values.put(SOURCE_UUID, envelope.getSourceUuid().orElse(null));
      values.put(DEVICE_ID, envelope.getSourceDevice());
      values.put(CONTENT, envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "");
      values.put(TIMESTAMP, envelope.getTimestamp());
      values.put(SERVER_RECEIVED_TIMESTAMP, envelope.getServerReceivedTimestamp());
      values.put(SERVER_DELIVERED_TIMESTAMP, envelope.getServerDeliveredTimestamp());
      values.put(SERVER_GUID, envelope.getServerGuid());

      return databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, values);
    }
  }

  public SignalServiceEnvelope get(long id) throws NoSuchMessageException {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, ID_WHERE,
                                                                new String[] {String.valueOf(id)},
                                                                null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        String legacyMessage = cursor.getString(cursor.getColumnIndexOrThrow(LEGACY_MSG));
        String content       = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));
        String uuid          = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE_UUID));
        String e164          = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE_E164));

        return new SignalServiceEnvelope(cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
                                         SignalServiceAddress.fromRaw(uuid, e164),
                                         cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID)),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)),
                                         Util.isEmpty(content) ? null : Base64.decode(content),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_RECEIVED_TIMESTAMP)),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_DELIVERED_TIMESTAMP)),
                                         cursor.getString(cursor.getColumnIndexOrThrow(SERVER_GUID)),
                                         "",
                                         true,
                                         false);
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
    return databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
  }

  public void delete(long id) {
    databaseHelper.getSignalWritableDatabase().delete(TABLE_NAME, ID_WHERE, new String[] {id+""});
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  private Optional<Long> find(SignalServiceEnvelope envelope) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = TYPE       + " = ? AND " +
                              DEVICE_ID  + " = ? AND " +
                              LEGACY_MSG + " = ? AND " +
                              CONTENT    + " = ? AND " +
                              TIMESTAMP  + " = ? AND " +
                              "(" + SOURCE_UUID + " NOT NULL AND " + SOURCE_UUID + " = ?)";

    String[]        args     = new String[] { String.valueOf(envelope.getType()),
                                              String.valueOf(envelope.getSourceDevice()),
                                              envelope.hasContent() ? Base64.encodeBytes(envelope.getContent()) : "",
                                              String.valueOf(envelope.getTimestamp()),
                                              String.valueOf(envelope.getSourceUuid().orElse(null)) };


    try (Cursor cursor = database.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return Optional.of(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));
      } else {
        return Optional.empty();
      }
    }
  }

  public static class Reader implements Closeable {
    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public SignalServiceEnvelope getNext() {
      try {
        if (cursor == null || !cursor.moveToNext())
          return null;

        int    type                     = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        String sourceUuid               = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE_UUID));
        String sourceE164               = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE_E164));
        int    deviceId                 = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
        String content                  = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));
        long   timestamp                = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
        long   serverReceivedTimestamp  = cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_RECEIVED_TIMESTAMP));
        long   serverDeliveredTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SERVER_DELIVERED_TIMESTAMP));
        String serverGuid               = cursor.getString(cursor.getColumnIndexOrThrow(SERVER_GUID));

        return new SignalServiceEnvelope(type,
                                         SignalServiceAddress.fromRaw(sourceUuid, sourceE164),
                                         deviceId,
                                         timestamp,
                                         content != null ? Base64.decode(content) : null,
                                         serverReceivedTimestamp,
                                         serverDeliveredTimestamp,
                                         serverGuid,
                                         "",
                                         true,
                                         false);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public void close() {
      this.cursor.close();
    }
  }
}
