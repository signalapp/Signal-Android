package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SqlUtil;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps track of which recipients are aware of which distributionIds. For the storage of sender
 * keys themselves, see {@link SenderKeyTable}.
 */
public class SenderKeySharedTable extends DatabaseTable {

  private static final String TAG = Log.tag(SenderKeySharedTable.class);

  public static final String TABLE_NAME = "sender_key_shared";

  private static final String ID              = "_id";
  public static final  String DISTRIBUTION_ID = "distribution_id";
  public static final  String ADDRESS         = "address";
  public static final  String DEVICE          = "device";
  public static final  String TIMESTAMP       = "timestamp";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID              + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 DISTRIBUTION_ID + " TEXT NOT NULL, " +
                                                                                 ADDRESS         + " TEXT NOT NULL, " +
                                                                                 DEVICE          + " INTEGER NOT NULL, " +
                                                                                 TIMESTAMP       + " INTEGER DEFAULT 0, " +
                                                                                 "UNIQUE(" + DISTRIBUTION_ID + "," + ADDRESS + ", " + DEVICE + ") ON CONFLICT REPLACE);";

  SenderKeySharedTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  /**
   * Mark that a distributionId has been shared with the provided recipients
   */
  public void markAsShared(@NonNull DistributionId distributionId, @NonNull Collection<SignalProtocolAddress> addresses) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      for (SignalProtocolAddress address : addresses) {
        ContentValues values = new ContentValues();
        values.put(ADDRESS, address.getName());
        values.put(DEVICE, address.getDeviceId());
        values.put(DISTRIBUTION_ID, distributionId.toString());
        values.put(TIMESTAMP, System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Get the set of recipientIds that know about the distributionId in question.
   */
  public @NonNull Set<SignalProtocolAddress> getSharedWith(@NonNull DistributionId distributionId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = DISTRIBUTION_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(distributionId);

    Set<SignalProtocolAddress> addresses = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ ADDRESS, DEVICE }, query, args, null, null, null)) {
      while (cursor.moveToNext()) {
        String address = CursorUtil.requireString(cursor, ADDRESS);
        int    device  = CursorUtil.requireInt(cursor, DEVICE);

        addresses.add(new SignalProtocolAddress(address, device));
      }
    }

    return addresses;
  }

  /**
   * Clear the shared statuses for all provided addresses.
   */
  public void delete(@NonNull DistributionId distributionId, @NonNull Collection<SignalProtocolAddress> addresses) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         query = DISTRIBUTION_ID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE + " = ?";

    db.beginTransaction();
    try {
      for (SignalProtocolAddress address : addresses) {
        db.delete(TABLE_NAME, query, SqlUtil.buildArgs(distributionId, address.getName(), address.getDeviceId()));
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Clear all shared statuses for a given distributionId.
   */
  public void deleteAllFor(@NonNull DistributionId distributionId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, DISTRIBUTION_ID + " = ?", SqlUtil.buildArgs(distributionId));
  }

  /**
   * Clear the shared status for all distributionIds for a set of addresses.
   */
  public void deleteAllFor(@NonNull Collection<SignalProtocolAddress> addresses) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         query = ADDRESS + " = ? AND " + DEVICE + " = ?";

    db.beginTransaction();
    try {
      for (SignalProtocolAddress address : addresses) {
        db.delete(TABLE_NAME, query, SqlUtil.buildArgs(address.getName(), address.getDeviceId()));
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Clear the shared status for all distributionIds for a given recipientId.
   */
  public void deleteAllFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
    Recipient      recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceId()) {
      db.delete(TABLE_NAME, ADDRESS + " = ?", SqlUtil.buildArgs(recipient.requireServiceId().toString()));
    } else {
      Log.w(TAG, "Recipient doesn't have a UUID! " + recipientId);
    }
  }

  /**
   * Clears all database content.
   */
  public void deleteAll() {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  /**
   * Gets the shared state of all of our sender keys. Used for debugging.
   */
  public Cursor getAllSharedWithCursor() {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    return db.query(TABLE_NAME, null, null, null, null, null, DISTRIBUTION_ID + ", " + ADDRESS + ", " + DEVICE);
  }
}
