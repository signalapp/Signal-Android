package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.PendingRetryReceiptModel;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;

/**
 * Holds information about messages we've sent out retry receipts for.
 */
public final class PendingRetryReceiptDatabase extends Database {

  public static final String TABLE_NAME = "pending_retry_receipts";

  private static final String ID                 = "_id";
  private static final String AUTHOR             = "author";
  private static final String DEVICE             = "device";
  private static final String SENT_TIMESTAMP     = "sent_timestamp";
  private static final String RECEIVED_TIMESTAMP = "received_timestamp";
  private static final String THREAD_ID          = "thread_id";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID                 + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 AUTHOR             + " TEXT NOT NULL, " +
                                                                                 DEVICE             + " INTEGER NOT NULL, " +
                                                                                 SENT_TIMESTAMP     + " INTEGER NOT NULL, " +
                                                                                 RECEIVED_TIMESTAMP + " TEXT NOT NULL, " +
                                                                                 THREAD_ID          + " INTEGER NOT NULL, " +
                                                                                 "UNIQUE(" + AUTHOR + "," + SENT_TIMESTAMP + ") ON CONFLICT REPLACE);";

  PendingRetryReceiptDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insert(@NonNull RecipientId author, int authorDevice, long sentTimestamp, long receivedTimestamp, long threadId) {
    ContentValues values = new ContentValues();
    values.put(AUTHOR, author.serialize());
    values.put(DEVICE, authorDevice);
    values.put(SENT_TIMESTAMP, sentTimestamp);
    values.put(RECEIVED_TIMESTAMP, receivedTimestamp);
    values.put(THREAD_ID, threadId);

    databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public @Nullable PendingRetryReceiptModel get(@NonNull RecipientId author, long sentTimestamp) {
    String   query = AUTHOR + " = ? AND " + SENT_TIMESTAMP + " = ?";
    String[] args  = SqlUtil.buildArgs(author, sentTimestamp);

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return fromCursor(cursor);
      }
    }

    return null;
  }

  public @Nullable PendingRetryReceiptModel getOldest() {
    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, RECEIVED_TIMESTAMP + " ASC", "1")) {
      if (cursor.moveToFirst()) {
        return fromCursor(cursor);
      }
    }

    return null;
  }

  public void delete(long id) {
    databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(id));
  }


  private static @NonNull PendingRetryReceiptModel fromCursor(@NonNull Cursor cursor) {
    return new PendingRetryReceiptModel(CursorUtil.requireLong(cursor, ID),
                                        RecipientId.from(CursorUtil.requireString(cursor, AUTHOR)),
                                        CursorUtil.requireInt(cursor, DEVICE),
                                        CursorUtil.requireLong(cursor, SENT_TIMESTAMP),
                                        CursorUtil.requireLong(cursor, RECEIVED_TIMESTAMP),
                                        CursorUtil.requireLong(cursor, THREAD_ID));
  }
}
