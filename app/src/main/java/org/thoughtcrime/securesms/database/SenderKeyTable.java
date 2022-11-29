package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SqlUtil;
import org.whispersystems.signalservice.api.push.DistributionId;

/**
 * Stores all of the sender keys -- both the ones we create, and the ones we're told about.
 *
 * When working with SenderKeys, keep this in mind: they're not *really* keys. They're sessions.
 * The name is largely historical, and there's too much momentum to change it.
 */
public class SenderKeyTable extends DatabaseTable {

  private static final String TAG = Log.tag(SenderKeyTable.class);

  public static final String TABLE_NAME = "sender_keys";

  private static final String ID              = "_id";
  public static final  String ADDRESS         = "address";
  public static final  String DEVICE          = "device";
  public static final  String DISTRIBUTION_ID = "distribution_id";
  public static final  String RECORD          = "record";
  public static final  String CREATED_AT      = "created_at";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID              + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 ADDRESS         + " TEXT NOT NULL, " +
                                                                                 DEVICE          + " INTEGER NOT NULL, " +
                                                                                 DISTRIBUTION_ID + " TEXT NOT NULL, " +
                                                                                 RECORD          + " BLOB NOT NULL, " +
                                                                                 CREATED_AT      + " INTEGER NOT NULL, " +
                                                                                 "UNIQUE(" + ADDRESS + "," + DEVICE + ", " + DISTRIBUTION_ID + ") ON CONFLICT REPLACE);";

  SenderKeyTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public void store(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId, @NonNull SenderKeyRecord record) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      ContentValues updateValues = new ContentValues();
      updateValues.put(RECORD, record.serialize());

      String   query       = ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
      String[] args        = SqlUtil.buildArgs(address.getName(), address.getDeviceId(), distributionId);
      int      updateCount = db.update(TABLE_NAME, updateValues, query, args);

      if (updateCount <= 0) {
        Log.d(TAG, "New sender key " + distributionId + " from " + address);

        ContentValues insertValues = new ContentValues();
        insertValues.put(ADDRESS, address.getName());
        insertValues.put(DEVICE, address.getDeviceId());
        insertValues.put(DISTRIBUTION_ID, distributionId.toString());
        insertValues.put(RECORD, record.serialize());
        insertValues.put(CREATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_NAME, null, insertValues, SQLiteDatabase.CONFLICT_REPLACE);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public @Nullable SenderKeyRecord load(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[] args  = SqlUtil.buildArgs(address.getName(), address.getDeviceId(), distributionId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ RECORD }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        try {
          return new SenderKeyRecord(CursorUtil.requireBlob(cursor, RECORD));
        } catch (InvalidMessageException e) {
          Log.w(TAG, e);
        }
      }
    }

    return null;
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public long getCreatedTime(@NonNull SignalProtocolAddress address, @NonNull DistributionId distributionId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[] args  = SqlUtil.buildArgs(address.getName(), address.getDeviceId(), distributionId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ CREATED_AT }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, CREATED_AT);
      }
    }

    return -1;
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull String addressName, @NonNull DistributionId distributionId) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         query = ADDRESS + " = ? AND " + DISTRIBUTION_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, distributionId);

    db.delete(TABLE_NAME, query, args);
  }

  /**
   * Get metadata for all sender keys created by the local user. Used for debugging.
   */
  public Cursor getAllCreatedBySelf() {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = ADDRESS + " = ?";
    String[]       args  = SqlUtil.buildArgs(SignalStore.account().requireAci());

    return db.query(TABLE_NAME, new String[]{ ID, DISTRIBUTION_ID, CREATED_AT }, query, args, null, null, CREATED_AT + " DESC");
  }

  /**
   * Deletes all database state.
   */
  public void deleteAll() {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }
}
