package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.thoughtcrime.securesms.database.model.PendingRetryReceiptModel;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SqlUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * Holds information about messages we've sent out retry receipts for.
 *
 * Do not use directly! The only class that should be accessing this is {@link PendingRetryReceiptCache}
 */
public final class PendingRetryReceiptTable extends DatabaseTable implements RecipientIdDatabaseReference, ThreadIdDatabaseReference {

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

  PendingRetryReceiptTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  @NonNull PendingRetryReceiptModel insert(@NonNull RecipientId author, int authorDevice, long sentTimestamp, long receivedTimestamp, long threadId) {
    ContentValues values = new ContentValues();
    values.put(AUTHOR, author.serialize());
    values.put(DEVICE, authorDevice);
    values.put(SENT_TIMESTAMP, sentTimestamp);
    values.put(RECEIVED_TIMESTAMP, receivedTimestamp);
    values.put(THREAD_ID, threadId);

    long id = databaseHelper.getSignalWritableDatabase().insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

    return new PendingRetryReceiptModel(id, author, authorDevice, sentTimestamp, receivedTimestamp, threadId);
  }

  @NonNull List<PendingRetryReceiptModel> getAll() {
    List<PendingRetryReceiptModel> models = new LinkedList<>();

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor.moveToNext()) {
        models.add(fromCursor(cursor));
      }
    }

    return models;
  }

  void delete(@NonNull PendingRetryReceiptModel model) {
    databaseHelper.getSignalWritableDatabase().delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(model.getId()));
  }

  private static @NonNull PendingRetryReceiptModel fromCursor(@NonNull Cursor cursor) {
    return new PendingRetryReceiptModel(CursorUtil.requireLong(cursor, ID),
                                        RecipientId.from(CursorUtil.requireString(cursor, AUTHOR)),
                                        CursorUtil.requireInt(cursor, DEVICE),
                                        CursorUtil.requireLong(cursor, SENT_TIMESTAMP),
                                        CursorUtil.requireLong(cursor, RECEIVED_TIMESTAMP),
                                        CursorUtil.requireLong(cursor, THREAD_ID));
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {
    ContentValues values = new ContentValues();
    values.put(AUTHOR, toId.serialize());
    getWritableDatabase().update(TABLE_NAME, values, AUTHOR + " = ?", SqlUtil.buildArgs(fromId));
    
    ApplicationDependencies.getPendingRetryReceiptCache().clear();
  }

  @Override
  public void remapThread(long fromId, long toId) {
    ContentValues values = new ContentValues();
    values.put(THREAD_ID, toId);
    getWritableDatabase().update(TABLE_NAME, values, THREAD_ID + " = ?", SqlUtil.buildArgs(fromId));

    ApplicationDependencies.getPendingRetryReceiptCache().clear();
  }
}
