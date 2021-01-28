package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import net.sqlcipher.Cursor;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

/**
 * Contains all databases necessary for full-text search (FTS).
 */
public class SearchDatabase extends Database {

  public static final String SMS_FTS_TABLE_NAME = "sms_fts";
  public static final String MMS_FTS_TABLE_NAME = "mms_fts";

  public static final String ID                     = "rowid";
  public static final String BODY                   = MmsSmsColumns.BODY;
  public static final String THREAD_ID              = MmsSmsColumns.THREAD_ID;
  public static final String SNIPPET                = "snippet";
  public static final String CONVERSATION_RECIPIENT = "conversation_recipient";
  public static final String MESSAGE_RECIPIENT      = "message_recipient";
  public static final String IS_MMS                 = "is_mms";
  public static final String MESSAGE_ID             = "message_id";

  public static final String SNIPPET_WRAP = "...";

  public static final String[] CREATE_TABLE = {
      "CREATE VIRTUAL TABLE " + SMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", " + THREAD_ID + " UNINDEXED, content=" + SmsDatabase.TABLE_NAME + ", content_rowid=" + SmsDatabase.ID + ");",

      "CREATE TRIGGER sms_ai AFTER INSERT ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + SmsDatabase.ID + ", new." + SmsDatabase.BODY + ", new." + SmsDatabase.THREAD_ID + ");\n" +
          "END;\n",
      "CREATE TRIGGER sms_ad AFTER DELETE ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + SmsDatabase.ID + ", old." + SmsDatabase.BODY + ", old." + SmsDatabase.THREAD_ID + ");\n" +
          "END;\n",
      "CREATE TRIGGER sms_au AFTER UPDATE ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + SmsDatabase.ID + ", old." + SmsDatabase.BODY + ", old." + SmsDatabase.THREAD_ID + ");\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES(new." + SmsDatabase.ID + ", new." + SmsDatabase.BODY + ", new." + SmsDatabase.THREAD_ID + ");\n" +
          "END;",


      "CREATE VIRTUAL TABLE " + MMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", " + THREAD_ID + " UNINDEXED, content=" + MmsDatabase.TABLE_NAME + ", content_rowid=" + MmsDatabase.ID + ");",

      "CREATE TRIGGER mms_ai AFTER INSERT ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + MmsDatabase.ID + ", new." + MmsDatabase.BODY + ", new." + MmsDatabase.THREAD_ID + ");\n" +
          "END;\n",
      "CREATE TRIGGER mms_ad AFTER DELETE ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + MmsDatabase.ID + ", old." + MmsDatabase.BODY + ", old." + MmsDatabase.THREAD_ID + ");\n" +
          "END;\n",
      "CREATE TRIGGER mms_au AFTER UPDATE ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES('delete', old." + MmsDatabase.ID + ", old." + MmsDatabase.BODY + ", old." + MmsDatabase.THREAD_ID + ");\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ", " + THREAD_ID + ") VALUES (new." + MmsDatabase.ID + ", new." + MmsDatabase.BODY + ", new." + MmsDatabase.THREAD_ID + ");\n" +
          "END;"
  };

  private static final String MESSAGES_QUERY =
      "SELECT " +
        ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
        MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
        "snippet(" + SMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
        SmsDatabase.TABLE_NAME + "." + SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
        SMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
        SMS_FTS_TABLE_NAME + "." + BODY + ", " +
        SMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
        "0 AS " + IS_MMS + " " +
      "FROM " + SmsDatabase.TABLE_NAME + " " +
      "INNER JOIN " + SMS_FTS_TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + ID + " = " + SmsDatabase.TABLE_NAME + "." + SmsDatabase.ID + " " +
      "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
      "WHERE " + SMS_FTS_TABLE_NAME + " MATCH ? " +
      "UNION ALL " +
      "SELECT " +
        ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
        MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
        "snippet(" + MMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
        MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
        MMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
        MMS_FTS_TABLE_NAME + "." + BODY + ", " +
        MMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
        "1 AS " + IS_MMS + " " +
      "FROM " + MmsDatabase.TABLE_NAME + " " +
      "INNER JOIN " + MMS_FTS_TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " " +
      "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
      "WHERE " + MMS_FTS_TABLE_NAME + " MATCH ? " +
      "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
      "LIMIT 500";

  private static final String MESSAGES_FOR_THREAD_QUERY =
      "SELECT " +
          ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
          MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
          "snippet(" + SMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
          SmsDatabase.TABLE_NAME + "." + SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
          SMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
          SMS_FTS_TABLE_NAME + "." + BODY + ", " +
          SMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
          "0 AS " + IS_MMS + " " +
        "FROM " + SmsDatabase.TABLE_NAME + " " +
        "INNER JOIN " + SMS_FTS_TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + ID + " = " + SmsDatabase.TABLE_NAME + "." + SmsDatabase.ID + " " +
        "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
        "WHERE " + SMS_FTS_TABLE_NAME + " MATCH ? AND " + SmsDatabase.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = ? " +
        "UNION ALL " +
        "SELECT " +
          ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID + " AS " + CONVERSATION_RECIPIENT + ", " +
          MmsSmsColumns.RECIPIENT_ID + " AS " + MESSAGE_RECIPIENT + ", " +
          "snippet(" + MMS_FTS_TABLE_NAME + ", -1, '', '', '" + SNIPPET_WRAP + "', 7) AS " + SNIPPET + ", " +
          MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
          MMS_FTS_TABLE_NAME + "." + THREAD_ID + ", " +
          MMS_FTS_TABLE_NAME + "." + BODY + ", " +
          MMS_FTS_TABLE_NAME + "." + ID + " AS " + MESSAGE_ID + ", " +
          "1 AS " + IS_MMS + " " +
        "FROM " + MmsDatabase.TABLE_NAME + " " +
        "INNER JOIN " + MMS_FTS_TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " " +
        "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
        "WHERE " + MMS_FTS_TABLE_NAME + " MATCH ? AND " + MmsDatabase.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = ? " +
        "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
        "LIMIT 500";

  public SearchDatabase(@NonNull Context context, @NonNull SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor queryMessages(@NonNull String query) {
    SQLiteDatabase db                  = databaseHelper.getReadableDatabase();
    String         fullTextSearchQuery = createFullTextSearchQuery(query);

    if (TextUtils.isEmpty(fullTextSearchQuery)) {
      return null;
    }

    Cursor cursor = db.rawQuery(MESSAGES_QUERY, new String[] { fullTextSearchQuery,
                                                               fullTextSearchQuery });

    setNotifyConversationListListeners(cursor);
    return cursor;
  }

  public Cursor queryMessages(@NonNull String query, long threadId) {
    SQLiteDatabase db                  = databaseHelper.getReadableDatabase();
    String         fullTextSearchQuery = createFullTextSearchQuery(query);

    if (TextUtils.isEmpty(fullTextSearchQuery)) {
      return null;
    }

    Cursor cursor = db.rawQuery(MESSAGES_FOR_THREAD_QUERY, new String[] { fullTextSearchQuery,
                                                                          String.valueOf(threadId),
                                                                          fullTextSearchQuery,
                                                                          String.valueOf(threadId) });

    setNotifyConversationListListeners(cursor);
    return cursor;
  }

  private static String createFullTextSearchQuery(@NonNull String query) {
    return Stream.of(query.split(" "))
                 .map(String::trim)
                 .filter(s -> s.length() > 0)
                 .map(SearchDatabase::fullTextSearchEscape)
                 .collect(StringBuilder::new, (sb, s) -> sb.append(s).append("* "))
                 .toString();
  }

  private static String fullTextSearchEscape(String s) {
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}

