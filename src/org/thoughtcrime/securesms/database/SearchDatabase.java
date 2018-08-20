package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

/**
 * Contains all databases necessary for full-text search (FTS).
 */
public class SearchDatabase extends Database {

  public static final String SMS_FTS_TABLE_NAME = "sms_fts";
  public static final String MMS_FTS_TABLE_NAME = "mms_fts";

  public static final String ID      = "rowid";
  public static final String BODY    = MmsSmsColumns.BODY;
  public static final String RANK    = "rank";
  public static final String SNIPPET = "snippet";

  public static final String[] CREATE_TABLE = {
      "CREATE VIRTUAL TABLE " + SMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", content=" + SmsDatabase.TABLE_NAME + ", content_rowid=" + SmsDatabase.ID + ");",

      "CREATE TRIGGER sms_ai AFTER INSERT ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ") VALUES (new." + SmsDatabase.ID + ", new." + SmsDatabase.BODY + ");\n" +
          "END;\n",
      "CREATE TRIGGER sms_ad AFTER DELETE ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ") VALUES('delete', old." + SmsDatabase.ID + ", old." + SmsDatabase.BODY + ");\n" +
          "END;\n",
      "CREATE TRIGGER sms_au AFTER UPDATE ON " + SmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + SMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ") VALUES('delete', old." + SmsDatabase.ID + ", old." + SmsDatabase.BODY + ");\n" +
          "  INSERT INTO " + SMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ") VALUES(new." + SmsDatabase.ID + ", new." + SmsDatabase.BODY + ");\n" +
          "END;",


      "CREATE VIRTUAL TABLE " + MMS_FTS_TABLE_NAME + " USING fts5(" + BODY + ", content=" + MmsDatabase.TABLE_NAME + ", content_rowid=" + MmsDatabase.ID + ");",

      "CREATE TRIGGER mms_ai AFTER INSERT ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ") VALUES (new." + MmsDatabase.ID + ", new." + MmsDatabase.BODY + ");\n" +
          "END;\n",
      "CREATE TRIGGER mms_ad AFTER DELETE ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ") VALUES('delete', old." + MmsDatabase.ID + ", old." + MmsDatabase.BODY + ");\n" +
          "END;\n",
      "CREATE TRIGGER mms_au AFTER UPDATE ON " + MmsDatabase.TABLE_NAME + " BEGIN\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + MMS_FTS_TABLE_NAME + ", " + ID + ", " + BODY + ") VALUES('delete', old." + MmsDatabase.ID + ", old." + MmsDatabase.BODY + ");\n" +
          "  INSERT INTO " + MMS_FTS_TABLE_NAME + "(" + ID + ", " + BODY + ") VALUES (new." + MmsDatabase.ID + ", new." + MmsDatabase.BODY + ");\n" +
          "END;"
  };

  private static final String MESSAGES_QUERY =
      "SELECT " +
        ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ADDRESS + ", " +
        "snippet(" + SMS_FTS_TABLE_NAME + ", -1, '', '', '...', 7) AS " + SNIPPET + ", " +
        SmsDatabase.TABLE_NAME + "." + SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
        MmsSmsColumns.THREAD_ID + " " +
      "FROM " + SmsDatabase.TABLE_NAME + " " +
      "INNER JOIN " + SMS_FTS_TABLE_NAME + " ON " + SMS_FTS_TABLE_NAME + "." + ID + " = " + SmsDatabase.TABLE_NAME + "." + SmsDatabase.ID + " " +
      "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + SmsDatabase.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
      "WHERE " + SMS_FTS_TABLE_NAME + " MATCH ? " +
      "UNION ALL " +
      "SELECT " +
        ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ADDRESS + ", " +
        "snippet(" + MMS_FTS_TABLE_NAME + ", -1, '', '', '...', 7) AS " + SNIPPET + ", " +
        MmsDatabase.TABLE_NAME + "." + MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + ", " +
        MmsSmsColumns.THREAD_ID + " " +
      "FROM " + MmsDatabase.TABLE_NAME + " " +
      "INNER JOIN " + MMS_FTS_TABLE_NAME + " ON " + MMS_FTS_TABLE_NAME + "." + ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " " +
      "INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + MmsDatabase.TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + " " +
      "WHERE " + MMS_FTS_TABLE_NAME + " MATCH ? " +
      "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
      "LIMIT 500";

  public SearchDatabase(@NonNull Context context, @NonNull SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor queryMessages(@NonNull String query) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    List<String> tokens      = Stream.of(query.split(" ")).filter(s -> s.trim().length() > 0).toList();
    String       prefixQuery = Util.join(tokens, "* ");

    prefixQuery += "*";

    Cursor cursor = db.rawQuery(MESSAGES_QUERY, new String[] { prefixQuery, prefixQuery });
    setNotifyConverationListListeners(cursor);
    return cursor;
  }
}

