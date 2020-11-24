package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SessionDatabase extends Database {

  private static final String TAG = SessionDatabase.class.getSimpleName();

  public static final String TABLE_NAME = "sessions";

  private static final String ID      = "_id";
  public static final  String ADDRESS = "address";
  public static final  String DEVICE  = "device";
  public static final  String RECORD  = "record";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      "(" + ID + " INTEGER PRIMARY KEY, " + ADDRESS + " TEXT NOT NULL, " +
      DEVICE + " INTEGER NOT NULL, " + RECORD + " BLOB NOT NULL, " +
      "UNIQUE(" + ADDRESS + "," + DEVICE + ") ON CONFLICT REPLACE);";

  SessionDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void store(@NonNull Address address, int deviceId, @NonNull SessionRecord record) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(ADDRESS, address.serialize());
    values.put(DEVICE, deviceId);
    values.put(RECORD, record.serialize());

    database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public @Nullable SessionRecord load(@NonNull Address address, int deviceId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME, new String[]{RECORD},
                                        ADDRESS + " = ? AND " + DEVICE + " = ?",
                                        new String[] {address.serialize(), String.valueOf(deviceId)},
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

  public @NonNull List<SessionRow> getAllFor(@NonNull Address address) {
    SQLiteDatabase   database = databaseHelper.getReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null,
                                        ADDRESS + " = ?",
                                        new String[] {address.serialize()},
                                        null, null, null))
    {
      while (cursor != null && cursor.moveToNext()) {
        try {
          results.add(new SessionRow(address,
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
          results.add(new SessionRow(Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS))),
                                     cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE)),
                                     new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<Integer> getSubDevices(@NonNull Address address) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    List<Integer>  results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {DEVICE},
                                        ADDRESS + " = ?",
                                        new String[] {address.serialize()},
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

  public void delete(@NonNull Address address, int deviceId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.delete(TABLE_NAME, ADDRESS + " = ? AND " + DEVICE + " = ?",
                    new String[] {address.serialize(), String.valueOf(deviceId)});
  }

  public void deleteAllFor(@NonNull Address address) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ADDRESS + " = ?", new String[] {address.serialize()});
  }

  public static final class SessionRow {
    private final Address       address;
    private final int           deviceId;
    private final SessionRecord record;

    public SessionRow(Address address, int deviceId, SessionRecord record) {
      this.address  = address;
      this.deviceId = deviceId;
      this.record   = record;
    }

    public Address getAddress() {
      return address;
    }

    public int getDeviceId() {
      return deviceId;
    }

    public SessionRecord getRecord() {
      return record;
    }
  }
}
