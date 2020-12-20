package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SessionDatabase extends Database {

  private static final String TAG = SessionDatabase.class.getSimpleName();

  public static final String TABLE_NAME = "sessions";

  private static final String ID           = "_id";
  public static final  String RECIPIENT_ID = "address";
  public static final  String DEVICE       = "device";
  public static final  String RECORD       = "record";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      "(" + ID + " INTEGER PRIMARY KEY, " + RECIPIENT_ID + " INTEGER NOT NULL, " +
      DEVICE + " INTEGER NOT NULL, " + RECORD + " BLOB NOT NULL, " +
      "UNIQUE(" + RECIPIENT_ID + "," + DEVICE + ") ON CONFLICT REPLACE);";

  SessionDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void store(@NonNull RecipientId recipientId, int deviceId, @NonNull SessionRecord record) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(DEVICE, deviceId);
    values.put(RECORD, record.serialize());

    database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public @Nullable SessionRecord load(@NonNull RecipientId recipientId, int deviceId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME, new String[]{RECORD},
                                        RECIPIENT_ID + " = ? AND " + DEVICE + " = ?",
                                        new String[] {recipientId.serialize(), String.valueOf(deviceId)},
                                        null, null, null))
    {
      if (cursor != null && cursor.moveToFirst()) {
        try {
          return new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  public @NonNull List<SessionRow> getAllFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase   database = databaseHelper.getReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null,
                                        RECIPIENT_ID + " = ?",
                                        new String[] {recipientId.serialize()},
                                        null, null, null))
    {
      while (cursor != null && cursor.moveToNext()) {
        try {
          results.add(new SessionRow(recipientId,
                                     cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE)),
                                     new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<SessionRow> getAll() {
    SQLiteDatabase   database = databaseHelper.getReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        try {
          results.add(new SessionRow(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID))),
                                     cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE)),
                                     new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<Integer> getSubDevices(@NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    List<Integer>  results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {DEVICE},
                                        RECIPIENT_ID + " = ?",
                                        new String[] {recipientId.serialize()},
                                        null, null, null))
    {
      while (cursor != null && cursor.moveToNext()) {
        int device = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE));

        if (device != SignalServiceAddress.DEFAULT_DEVICE_ID) {
          results.add(device);
        }
      }
    }

    return results;
  }

  public void delete(@NonNull RecipientId recipientId, int deviceId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.delete(TABLE_NAME, RECIPIENT_ID + " = ? AND " + DEVICE + " = ?",
                    new String[] {recipientId.serialize(), String.valueOf(deviceId)});
  }

  public void deleteAllFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, RECIPIENT_ID + " = ?", new String[] {recipientId.serialize()});
  }

  public boolean hasSessionFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String         query    = RECIPIENT_ID + " = ?";
    String[]       args     = SqlUtil.buildArgs(recipientId);

    try (Cursor cursor = database.query(TABLE_NAME, new String[] { ID }, query, args, null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public static final class SessionRow {
    private final RecipientId   recipientId;
    private final int           deviceId;
    private final SessionRecord record;

    public SessionRow(@NonNull RecipientId recipientId, int deviceId, SessionRecord record) {
      this.recipientId = recipientId;
      this.deviceId    = deviceId;
      this.record      = record;
    }

    public RecipientId getRecipientId() {
      return recipientId;
    }

    public int getDeviceId() {
      return deviceId;
    }

    public SessionRecord getRecord() {
      return record;
    }
  }
}
