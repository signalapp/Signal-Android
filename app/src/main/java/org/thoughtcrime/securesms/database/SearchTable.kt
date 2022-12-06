package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import org.intellij.lang.annotations.Language
import org.signal.core.util.SqlUtil

/**
 * Contains all databases necessary for full-text search (FTS).
 */
@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Handles updates via triggers
class SearchTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    const val SMS_FTS_TABLE_NAME = "sms_fts"
    const val MMS_FTS_TABLE_NAME = "mms_fts"
    const val ID = "rowid"
    const val BODY = MmsSmsColumns.BODY
    const val THREAD_ID = MmsSmsColumns.THREAD_ID
    const val SNIPPET = "snippet"
    const val CONVERSATION_RECIPIENT = "conversation_recipient"
    const val MESSAGE_RECIPIENT = "message_recipient"
    const val IS_MMS = "is_mms"
    const val MESSAGE_ID = "message_id"
    const val SNIPPET_WRAP = "..."

    @Language("sql")
    val CREATE_TABLE = arrayOf(
      "CREATE VIRTUAL TABLE $SMS_FTS_TABLE_NAME USING fts5($BODY, $THREAD_ID UNINDEXED, content=${SmsTable.TABLE_NAME}, content_rowid=${SmsTable.ID})",
      "CREATE VIRTUAL TABLE $MMS_FTS_TABLE_NAME USING fts5($BODY, $THREAD_ID UNINDEXED, content=${MmsTable.TABLE_NAME}, content_rowid=${MmsTable.ID})",
    )

    @Language("sql")
    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER sms_ai AFTER INSERT ON ${SmsTable.TABLE_NAME} BEGIN
          INSERT INTO $SMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${SmsTable.ID}, new.${SmsTable.BODY}, new.${SmsTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER sms_ad AFTER DELETE ON ${SmsTable.TABLE_NAME} BEGIN
          INSERT INTO $SMS_FTS_TABLE_NAME($SMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${SmsTable.ID}, old.${SmsTable.BODY}, old.${SmsTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER sms_au AFTER UPDATE ON ${SmsTable.TABLE_NAME} BEGIN
          INSERT INTO $SMS_FTS_TABLE_NAME($SMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${SmsTable.ID}, old.${SmsTable.BODY}, old.${SmsTable.THREAD_ID});
          INSERT INTO $SMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES(new.${SmsTable.ID}, new.${SmsTable.BODY}, new.${SmsTable.THREAD_ID});
          END;
      """,
      """
        CREATE TRIGGER mms_ai AFTER INSERT ON ${MmsTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MmsTable.ID}, new.${MmsTable.BODY}, new.${MmsTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER mms_ad AFTER DELETE ON ${MmsTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($MMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MmsTable.ID}, old.${MmsTable.BODY}, old.${MmsTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER mms_au AFTER UPDATE ON ${MmsTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($MMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MmsTable.ID}, old.${MmsTable.BODY}, old.${MmsTable.THREAD_ID});
          INSERT INTO $MMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MmsTable.ID}, new.${MmsTable.BODY}, new.${MmsTable.THREAD_ID});
          END;
      """
    )

    private const val MESSAGES_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${SmsTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
        snippet($SMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET, 
        ${SmsTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $SMS_FTS_TABLE_NAME.$THREAD_ID, 
        $SMS_FTS_TABLE_NAME.$BODY, 
        $SMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID, 
        0 AS $IS_MMS 
      FROM 
        ${SmsTable.TABLE_NAME} 
          INNER JOIN $SMS_FTS_TABLE_NAME ON $SMS_FTS_TABLE_NAME.$ID = ${SmsTable.TABLE_NAME}.${SmsTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $SMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $SMS_FTS_TABLE_NAME MATCH ? AND 
        ${SmsTable.TABLE_NAME}.${SmsTable.TYPE} & ${MmsSmsColumns.Types.GROUP_V2_BIT} = 0 AND 
        ${SmsTable.TABLE_NAME}.${SmsTable.TYPE} & ${MmsSmsColumns.Types.BASE_TYPE_MASK} != ${MmsSmsColumns.Types.PROFILE_CHANGE_TYPE} AND 
        ${SmsTable.TABLE_NAME}.${SmsTable.TYPE} & ${MmsSmsColumns.Types.BASE_TYPE_MASK} != ${MmsSmsColumns.Types.GROUP_CALL_TYPE} 
      UNION ALL 
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MmsTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
        snippet($MMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET, 
        ${MmsTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $MMS_FTS_TABLE_NAME.$THREAD_ID, 
        $MMS_FTS_TABLE_NAME.$BODY, 
        $MMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID, 
        1 AS $IS_MMS 
      FROM 
        ${MmsTable.TABLE_NAME} 
          INNER JOIN $MMS_FTS_TABLE_NAME ON $MMS_FTS_TABLE_NAME.$ID = ${MmsTable.TABLE_NAME}.${MmsTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $MMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $MMS_FTS_TABLE_NAME MATCH ? AND 
        ${MmsTable.TABLE_NAME}.${MmsTable.TYPE} & ${MmsSmsColumns.Types.GROUP_V2_BIT} = 0 AND 
        ${MmsTable.TABLE_NAME}.${MmsTable.TYPE} & ${MmsSmsColumns.Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} = 0 
      ORDER BY ${MmsSmsColumns.DATE_RECEIVED} DESC 
      LIMIT 500
    """

    private const val MESSAGES_FOR_THREAD_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${SmsTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
        snippet($SMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET, 
        ${SmsTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $SMS_FTS_TABLE_NAME.$THREAD_ID, 
        $SMS_FTS_TABLE_NAME.$BODY,
        $SMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID, 
        0 AS $IS_MMS 
      FROM 
        ${SmsTable.TABLE_NAME} 
          INNER JOIN $SMS_FTS_TABLE_NAME ON $SMS_FTS_TABLE_NAME.$ID = ${SmsTable.TABLE_NAME}.${SmsTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $SMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $SMS_FTS_TABLE_NAME MATCH ? AND 
        ${SmsTable.TABLE_NAME}.${MmsSmsColumns.THREAD_ID} = ? 
      UNION ALL 
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MmsTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT,
        snippet($MMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET,
        ${MmsTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $MMS_FTS_TABLE_NAME.$THREAD_ID, 
        $MMS_FTS_TABLE_NAME.$BODY, 
        $MMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID,
        1 AS $IS_MMS 
      FROM 
        ${MmsTable.TABLE_NAME} 
          INNER JOIN $MMS_FTS_TABLE_NAME ON $MMS_FTS_TABLE_NAME.$ID = ${MmsTable.TABLE_NAME}.${MmsTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $MMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $MMS_FTS_TABLE_NAME MATCH ? AND 
        ${MmsTable.TABLE_NAME}.${MmsSmsColumns.THREAD_ID} = ? 
      ORDER BY ${MmsSmsColumns.DATE_RECEIVED} DESC 
      LIMIT 500
    """
  }

  fun queryMessages(query: String): Cursor? {
    val fullTextSearchQuery = createFullTextSearchQuery(query)
    return if (fullTextSearchQuery.isEmpty()) {
      null
    } else {
      readableDatabase.rawQuery(MESSAGES_QUERY, SqlUtil.buildArgs(fullTextSearchQuery, fullTextSearchQuery))
    }
  }

  fun queryMessages(query: String, threadId: Long): Cursor? {
    val fullTextSearchQuery = createFullTextSearchQuery(query)
    return if (TextUtils.isEmpty(fullTextSearchQuery)) {
      null
    } else {
      readableDatabase.rawQuery(MESSAGES_FOR_THREAD_QUERY, SqlUtil.buildArgs(fullTextSearchQuery, threadId, fullTextSearchQuery, threadId))
    }
  }

  private fun createFullTextSearchQuery(query: String): String {
    return query
      .split(" ")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { fullTextSearchEscape(it) }
      .joinToString(
        separator = " ",
        transform = { "$it*" }
      )
  }

  private fun fullTextSearchEscape(s: String): String {
    return "\"${s.replace("\"", "\"\"")}\""
  }
}
