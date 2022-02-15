package org.thoughtcrime.securesms.database;


import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class SessionDatabase extends Database {

  private static final String TAG = Log.tag(SessionDatabase.class);

  public static final String TABLE_NAME = "sessions";

  private static final String ID      = "_id";
  public static final  String ADDRESS = "address";
  public static final  String DEVICE  = "device";
  public static final  String RECORD  = "record";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID      + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 ADDRESS + " TEXT NOT NULL, " +
                                                                                 DEVICE  + " INTEGER NOT NULL, " +
                                                                                 RECORD  + " BLOB NOT NULL, " +
                                                                                 "UNIQUE(" + ADDRESS + "," + DEVICE + "));";

  SessionDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public void store(@NonNull SignalProtocolAddress address, @NonNull SessionRecord record) {
    if (address.getName().charAt(0) == '+') {
      throw new IllegalArgumentException("Cannot insert an e164 into this table!");
    }

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    try (SQLiteStatement statement = db.compileStatement("INSERT INTO " + TABLE_NAME + " (" + ADDRESS + ", " + DEVICE + ", " + RECORD + ") VALUES (?, ?, ?) " +
                                                         "ON CONFLICT (" + ADDRESS + ", " + DEVICE + ") DO UPDATE SET " + RECORD + " = excluded." + RECORD))
    {
      statement.bindString(1, address.getName());
      statement.bindLong(2, address.getDeviceId());
      statement.bindBlob(3, record.serialize());
      statement.execute();
    }
  }

  public @Nullable SessionRecord load(@NonNull SignalProtocolAddress address) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[] { RECORD };
    String         selection  = ADDRESS + " = ? AND " + DEVICE + " = ?";
    String[]       args       = SqlUtil.buildArgs(address.getName(), address.getDeviceId());

    try (Cursor cursor = database.query(TABLE_NAME, projection, selection, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        try {
          return new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  public @NonNull List<SessionRecord> load(@NonNull List<SignalProtocolAddress> addresses) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = ADDRESS + " = ? AND " + DEVICE + " = ?";
    List<String[]> args     = new ArrayList<>(addresses.size());

    HashMap<SignalProtocolAddress, SessionRecord> sessions = new LinkedHashMap<>(addresses.size());

    for (SignalProtocolAddress address : addresses) {
      args.add(SqlUtil.buildArgs(address.getName(), address.getDeviceId()));
      sessions.put(address, null);
    }

    String[] projection = new String[] { ADDRESS, DEVICE, RECORD };

    for (SqlUtil.Query combinedQuery : SqlUtil.buildCustomCollectionQuery(query, args)) {
      try (Cursor cursor = database.query(TABLE_NAME, projection, combinedQuery.getWhere(), combinedQuery.getWhereArgs(), null, null, null)) {
        while (cursor.moveToNext()) {
          String address = CursorUtil.requireString(cursor, ADDRESS);
          int    device  = CursorUtil.requireInt(cursor, DEVICE);

          try {
            SessionRecord record = new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)));
            sessions.put(new SignalProtocolAddress(address, device), record);
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }
      }
    }

    return new ArrayList<>(sessions.values());
  }

  public @NonNull List<SessionRow> getAllFor(@NonNull String addressName) {
    SQLiteDatabase   database = databaseHelper.getSignalReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, ADDRESS + " = ?", SqlUtil.buildArgs(addressName), null, null, null)) {
      while (cursor.moveToNext()) {
        try {
          results.add(new SessionRow(CursorUtil.requireString(cursor, ADDRESS),
                                     CursorUtil.requireInt(cursor, DEVICE),
                                     new SessionRecord(CursorUtil.requireBlob(cursor, RECORD))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<SessionRow> getAllFor(@NonNull List<String> addressNames) {
    SQLiteDatabase   database = databaseHelper.getSignalReadableDatabase();
    SqlUtil.Query    query    = SqlUtil.buildCollectionQuery(ADDRESS, addressNames);
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, query.getWhere(), query.getWhereArgs(), null, null, null)) {
      while (cursor.moveToNext()) {
        try {
          results.add(new SessionRow(CursorUtil.requireString(cursor, ADDRESS),
                                     CursorUtil.requireInt(cursor, DEVICE),
                                     new SessionRecord(CursorUtil.requireBlob(cursor, RECORD))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<SessionRow> getAll() {
    SQLiteDatabase   database = databaseHelper.getSignalReadableDatabase();
    List<SessionRow> results  = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor.moveToNext()) {
        try {
          results.add(new SessionRow(CursorUtil.requireString(cursor, ADDRESS),
                                     CursorUtil.requireInt(cursor, DEVICE),
                                     new SessionRecord(cursor.getBlob(cursor.getColumnIndexOrThrow(RECORD)))));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }
    }

    return results;
  }

  public @NonNull List<Integer> getSubDevices(@NonNull String addressName) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    List<Integer>  results    = new LinkedList<>();
    String[]       projection = new String[] { DEVICE };
    String         selection  = ADDRESS + " = ?";
    String[]       args       = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, projection, selection, args, null, null, null)) {
      while (cursor.moveToNext()) {
        int device = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE));

        if (device != SignalServiceAddress.DEFAULT_DEVICE_ID) {
          results.add(device);
        }
      }
    }

    return results;
  }

  public void delete(@NonNull SignalProtocolAddress address) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    String         selection  = ADDRESS + " = ? AND " + DEVICE + " = ?";
    String[]       args       = SqlUtil.buildArgs(address.getName(), address.getDeviceId());


    database.delete(TABLE_NAME, selection, args);
  }

  public void deleteAllFor(@NonNull String addressName) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, ADDRESS + " = ?", SqlUtil.buildArgs(addressName));
  }

  public boolean hasSessionFor(@NonNull String addressName) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = ADDRESS + " = ?";
    String[]       args     = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, new String[] { "1" }, query, args, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  public static final class SessionRow {
    private final String        address;
    private final int           deviceId;
    private final SessionRecord record;

    public SessionRow(@NonNull String address, int deviceId, SessionRecord record) {
      this.address  = address;
      this.deviceId = deviceId;
      this.record   = record;
    }

    public @NonNull String getAddress() {
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
