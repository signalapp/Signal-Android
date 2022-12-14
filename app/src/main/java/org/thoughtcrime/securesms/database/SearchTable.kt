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
      "CREATE VIRTUAL TABLE $MMS_FTS_TABLE_NAME USING fts5($BODY, $THREAD_ID UNINDEXED, content=${MessageTable.TABLE_NAME}, content_rowid=${MessageTable.ID})",
    )

    @Language("sql")
    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER mms_ai AFTER INSERT ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MessageTable.ID}, new.${MessageTable.BODY}, new.${MessageTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER mms_ad AFTER DELETE ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($MMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MessageTable.ID}, old.${MessageTable.BODY}, old.${MessageTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER mms_au AFTER UPDATE ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $MMS_FTS_TABLE_NAME($MMS_FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MessageTable.ID}, old.${MessageTable.BODY}, old.${MessageTable.THREAD_ID});
          INSERT INTO $MMS_FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MessageTable.ID}, new.${MessageTable.BODY}, new.${MessageTable.THREAD_ID});
          END;
      """
    )

    private const val MESSAGES_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MessageTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
        snippet($MMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET, 
        ${MessageTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $MMS_FTS_TABLE_NAME.$THREAD_ID, 
        $MMS_FTS_TABLE_NAME.$BODY, 
        $MMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID, 
        1 AS $IS_MMS 
      FROM 
        ${MessageTable.TABLE_NAME} 
          INNER JOIN $MMS_FTS_TABLE_NAME ON $MMS_FTS_TABLE_NAME.$ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $MMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $MMS_FTS_TABLE_NAME MATCH ? AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MmsSmsColumns.Types.GROUP_V2_BIT} = 0 AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MmsSmsColumns.Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} = 0 
      ORDER BY ${MmsSmsColumns.DATE_RECEIVED} DESC 
      LIMIT 500
    """

    private const val MESSAGES_FOR_THREAD_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MessageTable.TABLE_NAME}.${MmsSmsColumns.RECIPIENT_ID} AS $MESSAGE_RECIPIENT,
        snippet($MMS_FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET,
        ${MessageTable.TABLE_NAME}.${MmsSmsColumns.DATE_RECEIVED}, 
        $MMS_FTS_TABLE_NAME.$THREAD_ID, 
        $MMS_FTS_TABLE_NAME.$BODY, 
        $MMS_FTS_TABLE_NAME.$ID AS $MESSAGE_ID,
        1 AS $IS_MMS 
      FROM 
        ${MessageTable.TABLE_NAME} 
          INNER JOIN $MMS_FTS_TABLE_NAME ON $MMS_FTS_TABLE_NAME.$ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $MMS_FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $MMS_FTS_TABLE_NAME MATCH ? AND 
        ${MessageTable.TABLE_NAME}.${MmsSmsColumns.THREAD_ID} = ? 
      ORDER BY ${MmsSmsColumns.DATE_RECEIVED} DESC 
      LIMIT 500
    """
  }

  fun queryMessages(query: String): Cursor? {
    val fullTextSearchQuery = createFullTextSearchQuery(query)
    return if (fullTextSearchQuery.isEmpty()) {
      null
    } else {
      readableDatabase.rawQuery(MESSAGES_QUERY, SqlUtil.buildArgs(fullTextSearchQuery))
    }
  }

  fun queryMessages(query: String, threadId: Long): Cursor? {
    val fullTextSearchQuery = createFullTextSearchQuery(query)
    return if (TextUtils.isEmpty(fullTextSearchQuery)) {
      null
    } else {
      readableDatabase.rawQuery(MESSAGES_FOR_THREAD_QUERY, SqlUtil.buildArgs(fullTextSearchQuery, threadId))
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
