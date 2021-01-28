package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.Pair;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class GroupReceiptDatabase extends Database {

  public  static final String TABLE_NAME = "group_receipts";

  private static final String ID           = "_id";
  public  static final String MMS_ID       = "mms_id";
          static final String RECIPIENT_ID = "address";
  private static final String STATUS       = "status";
  private static final String TIMESTAMP    = "timestamp";
  private static final String UNIDENTIFIED = "unidentified";

  public static final int STATUS_UNKNOWN     = -1;
  public static final int STATUS_UNDELIVERED = 0;
  public static final int STATUS_DELIVERED   = 1;
  public static final int STATUS_READ        = 2;
  public static final int STATUS_VIEWED      = 3;

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "                          +
      MMS_ID + " INTEGER, " + RECIPIENT_ID + " INTEGER, " + STATUS + " INTEGER, " + TIMESTAMP + " INTEGER, " + UNIDENTIFIED + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXES = {
      "CREATE INDEX IF NOT EXISTS group_receipt_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
  };

  public GroupReceiptDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insert(Collection<RecipientId> recipientIds, long mmsId, int status, long timestamp) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (RecipientId recipientId : recipientIds) {
        ContentValues values = new ContentValues(4);
        values.put(MMS_ID, mmsId);
        values.put(RECIPIENT_ID, recipientId.serialize());
        values.put(STATUS, status);
        values.put(TIMESTAMP, timestamp);

        db.insert(TABLE_NAME, null, values);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void update(@NonNull RecipientId recipientId, long mmsId, int status, long timestamp) {
    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    ContentValues  values = new ContentValues(2);
    values.put(STATUS, status);
    values.put(TIMESTAMP, timestamp);

    db.update(TABLE_NAME, values, MMS_ID + " = ? AND " + RECIPIENT_ID + " = ? AND " + STATUS + " < ?",
              new String[] {String.valueOf(mmsId), recipientId.serialize(), String.valueOf(status)});
  }

  public void setUnidentified(Collection<Pair<RecipientId, Boolean>> results, long mmsId) {
    SQLiteDatabase db  = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      String query = MMS_ID + " = ? AND " + RECIPIENT_ID + " = ?";

      for (Pair<RecipientId, Boolean> result : results) {
        ContentValues values = new ContentValues(1);
        values.put(UNIDENTIFIED, result.second() ? 1 : 0);

        db.update(TABLE_NAME, values, query, new String[]{ String.valueOf(mmsId), result.first().serialize()});
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public @NonNull List<GroupReceiptInfo> getGroupReceiptInfo(long mmsId) {
    SQLiteDatabase         db      = databaseHelper.getReadableDatabase();
    List<GroupReceiptInfo> results = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, null, MMS_ID + " = ?", new String[] {String.valueOf(mmsId)}, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        results.add(new GroupReceiptInfo(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID))),
                                         cursor.getInt(cursor.getColumnIndexOrThrow(STATUS)),
                                         cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)),
                                         cursor.getInt(cursor.getColumnIndexOrThrow(UNIDENTIFIED)) == 1));
      }
    }

    return results;
  }

  void deleteRowsForMessage(long mmsId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, MMS_ID + " = ?", new String[] {String.valueOf(mmsId)});
  }

  void deleteAbandonedRows() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, MMS_ID + " NOT IN (SELECT " + MmsDatabase.ID + " FROM " + MmsDatabase.TABLE_NAME + ")", null);
  }

  void deleteAllRows() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  public static class GroupReceiptInfo {
    private final RecipientId recipientId;
    private final int         status;
    private final long        timestamp;
    private final boolean     unidentified;

    GroupReceiptInfo(@NonNull RecipientId recipientId, int status, long timestamp, boolean unidentified) {
      this.recipientId  = recipientId;
      this.status       = status;
      this.timestamp    = timestamp;
      this.unidentified = unidentified;
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public int getStatus() {
      return status;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public boolean isUnidentified() {
      return unidentified;
    }
  }
}
