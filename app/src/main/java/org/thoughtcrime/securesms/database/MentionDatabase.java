package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MentionDatabase extends Database {

  static final String TABLE_NAME = "mention";

  private static final String ID           = "_id";
          static final String THREAD_ID    = "thread_id";
  private static final String MESSAGE_ID   = "message_id";
          static final String RECIPIENT_ID = "recipient_id";
  private static final String RANGE_START  = "range_start";
  private static final String RANGE_LENGTH = "range_length";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID           + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                 THREAD_ID    + " INTEGER, " +
                                                                                 MESSAGE_ID   + " INTEGER, " +
                                                                                 RECIPIENT_ID + " INTEGER, " +
                                                                                 RANGE_START  + " INTEGER, " +
                                                                                 RANGE_LENGTH + " INTEGER)";

  public static final String[] CREATE_INDEXES = new String[] {
    "CREATE INDEX IF NOT EXISTS mention_message_id_index ON " + TABLE_NAME + " (" + MESSAGE_ID + ");",
    "CREATE INDEX IF NOT EXISTS mention_recipient_id_thread_id_index ON " + TABLE_NAME + " (" + RECIPIENT_ID + ", " + THREAD_ID + ");"
  };

  public MentionDatabase(@NonNull Context context, @NonNull SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public void insert(long threadId, long messageId, @NonNull Collection<Mention> mentions) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (Mention mention : mentions) {
        ContentValues values = new ContentValues();
        values.put(THREAD_ID, threadId);
        values.put(MESSAGE_ID, messageId);
        values.put(RECIPIENT_ID, mention.getRecipientId().toLong());
        values.put(RANGE_START, mention.getStart());
        values.put(RANGE_LENGTH, mention.getLength());
        db.insert(TABLE_NAME, null, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public @NonNull List<Mention> getMentionsForMessage(long messageId) {
    SQLiteDatabase db       = databaseHelper.getReadableDatabase();
    List<Mention>  mentions = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, null, MESSAGE_ID + " = ?", SqlUtil.buildArgs(messageId), null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        mentions.add(new Mention(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)),
                                 CursorUtil.requireInt(cursor, RANGE_START),
                                 CursorUtil.requireInt(cursor, RANGE_LENGTH)));
      }
    }

    return mentions;
  }

  public @NonNull Map<Long, List<Mention>> getMentionsForMessages(@NonNull Collection<Long> messageIds) {
    SQLiteDatabase db  = databaseHelper.getReadableDatabase();
    String         ids = TextUtils.join(",", messageIds);

    try (Cursor cursor = db.query(TABLE_NAME, null, MESSAGE_ID + " IN (" + ids + ")", null, null, null, null)) {
      return readMentions(cursor);
    }
  }

  public @NonNull Map<Long, List<Mention>> getMentionsContainingRecipients(@NonNull Collection<RecipientId> recipientIds, long limit) {
    return getMentionsContainingRecipients(recipientIds, -1, limit);
  }

  public @NonNull Map<Long, List<Mention>> getMentionsContainingRecipients(@NonNull Collection<RecipientId> recipientIds, long threadId, long limit) {
    SQLiteDatabase db  = databaseHelper.getReadableDatabase();
    String         ids = TextUtils.join(",", Stream.of(recipientIds).map(RecipientId::serialize).toList());

    String where = " WHERE " + RECIPIENT_ID + " IN (" + ids + ")";
    if (threadId != -1) {
      where += " AND " + THREAD_ID + " = " + threadId;
    }

    String subSelect = "SELECT DISTINCT " + MESSAGE_ID +
                       " FROM " + TABLE_NAME +
                       where +
                       " ORDER BY " + ID + " DESC" +
                       " LIMIT " + limit;

    String query = "SELECT *" +
                   " FROM " + TABLE_NAME +
                   " WHERE " + MESSAGE_ID +
                   " IN (" + subSelect + ")";

    try (Cursor cursor = db.rawQuery(query, null)) {
      return readMentions(cursor);
    }
  }

  void deleteMentionsForMessage(long messageId) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         where = MESSAGE_ID + " = ?";

    db.delete(TABLE_NAME, where, SqlUtil.buildArgs(messageId));
  }

  void deleteAbandonedMentions() {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         where = MESSAGE_ID + " NOT IN (SELECT " + MmsDatabase.ID + " FROM " + MmsDatabase.TABLE_NAME + ") OR " + THREAD_ID + " NOT IN (SELECT " + ThreadDatabase.ID + " FROM " + ThreadDatabase.TABLE_NAME + ")";

    db.delete(TABLE_NAME, where, null);
  }

  void deleteAllMentions() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  private @NonNull Map<Long, List<Mention>> readMentions(@Nullable Cursor cursor) {
    Map<Long, List<Mention>> mentions = new HashMap<>();
    while (cursor != null && cursor.moveToNext()) {
      long          messageId       = CursorUtil.requireLong(cursor, MESSAGE_ID);
      List<Mention> messageMentions = mentions.get(messageId);

      if (messageMentions == null) {
        messageMentions = new LinkedList<>();
        mentions.put(messageId, messageMentions);
      }

      messageMentions.add(new Mention(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)),
                                      CursorUtil.requireInt(cursor, RANGE_START),
                                      CursorUtil.requireInt(cursor, RANGE_LENGTH)));
    }
    return mentions;
  }
}
