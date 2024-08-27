package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.intellij.lang.annotations.Language
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.RebuildMessageSearchIndexJob

/**
 * Contains all databases necessary for full-text search (FTS).
 */
@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Handles updates via triggers
class SearchTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(SearchTable::class.java)

    const val FTS_TABLE_NAME = "message_fts"
    const val ID = "rowid"
    const val BODY = MessageTable.BODY
    const val THREAD_ID = MessageTable.THREAD_ID
    const val SNIPPET = "snippet"
    const val CONVERSATION_RECIPIENT = "conversation_recipient"
    const val MESSAGE_RECIPIENT = "message_recipient"
    const val IS_MMS = "is_mms"
    const val MESSAGE_ID = "message_id"
    const val SNIPPET_WRAP = "..."

    @Language("sql")
    val CREATE_TABLE = arrayOf(
      // We've taken the default of tokenize value of "unicode61 categories 'L* N* Co'" and added the Sc (currency) and So (emoji) categories to allow searching for those characters.
      // https://www.sqlite.org/fts5.html#tokenizers
      // https://www.compart.com/en/unicode/category
      """CREATE VIRTUAL TABLE $FTS_TABLE_NAME USING fts5($BODY, $THREAD_ID UNINDEXED, content=${MessageTable.TABLE_NAME}, content_rowid=${MessageTable.ID}, tokenize = "unicode61 categories 'L* N* Co Sc So'")""",

      // Not technically a `CREATE` statement, but it's part of table creation. FTS5 just has weird configuration syntax. See https://www.sqlite.org/fts5.html#the_secure_delete_configuration_option
      """INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) VALUES('secure-delete', 1);"""
    )

    private const val TRIGGER_AFTER_INSERT = "message_ai"
    private const val TRIGGER_AFTER_DELETE = "message_ad"
    private const val TRIGGER_AFTER_UPDATE = "message_au"

    @Language("sql")
    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER $TRIGGER_AFTER_INSERT AFTER INSERT ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MessageTable.ID}, new.${MessageTable.BODY}, new.${MessageTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER $TRIGGER_AFTER_DELETE AFTER DELETE ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $FTS_TABLE_NAME($FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MessageTable.ID}, old.${MessageTable.BODY}, old.${MessageTable.THREAD_ID});
        END;
      """,
      """
        CREATE TRIGGER $TRIGGER_AFTER_UPDATE AFTER UPDATE ON ${MessageTable.TABLE_NAME} BEGIN
          INSERT INTO $FTS_TABLE_NAME($FTS_TABLE_NAME, $ID, $BODY, $THREAD_ID) VALUES('delete', old.${MessageTable.ID}, old.${MessageTable.BODY}, old.${MessageTable.THREAD_ID});
          INSERT INTO $FTS_TABLE_NAME($ID, $BODY, $THREAD_ID) VALUES (new.${MessageTable.ID}, new.${MessageTable.BODY}, new.${MessageTable.THREAD_ID});
        END;
      """
    )

    @Language("sql")
    private const val MESSAGES_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
        snippet($FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET, 
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED}, 
        $FTS_TABLE_NAME.$THREAD_ID, 
        $FTS_TABLE_NAME.$BODY, 
        $FTS_TABLE_NAME.$ID AS $MESSAGE_ID, 
        1 AS $IS_MMS 
      FROM 
        ${MessageTable.TABLE_NAME} 
          INNER JOIN $FTS_TABLE_NAME ON $FTS_TABLE_NAME.$ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $FTS_TABLE_NAME MATCH ? AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MessageTypes.GROUP_V2_BIT} = 0 AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} = 0 AND
        ${MessageTable.TABLE_NAME}.${MessageTable.SCHEDULED_DATE} < 0 AND
        ${MessageTable.TABLE_NAME}.${MessageTable.LATEST_REVISION_ID} IS NULL
      ORDER BY ${MessageTable.DATE_RECEIVED} DESC 
      LIMIT 500
    """

    @Language("sql")
    private const val MESSAGES_FOR_THREAD_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID} AS $MESSAGE_RECIPIENT,
        snippet($FTS_TABLE_NAME, -1, '', '', '$SNIPPET_WRAP', 7) AS $SNIPPET,
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED}, 
        $FTS_TABLE_NAME.$THREAD_ID, 
        $FTS_TABLE_NAME.$BODY, 
        $FTS_TABLE_NAME.$ID AS $MESSAGE_ID,
        1 AS $IS_MMS 
      FROM 
        ${MessageTable.TABLE_NAME} 
          INNER JOIN $FTS_TABLE_NAME ON $FTS_TABLE_NAME.$ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID} 
          INNER JOIN ${ThreadTable.TABLE_NAME} ON $FTS_TABLE_NAME.$THREAD_ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} 
      WHERE 
        $FTS_TABLE_NAME MATCH ? AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = ? AND
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MessageTypes.GROUP_V2_BIT} = 0 AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} = 0 AND
        ${MessageTable.TABLE_NAME}.${MessageTable.SCHEDULED_DATE} < 0 AND
        ${MessageTable.TABLE_NAME}.${MessageTable.LATEST_REVISION_ID} IS NULL
      ORDER BY ${MessageTable.DATE_RECEIVED} DESC 
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

  /**
   * Re-adds every message to the index. It's fine to insert the same message twice; the table will naturally de-dupe.
   *
   * In order to prevent the database from locking up with super large inserts, this will perform the re-index in batches of the size you specify.
   * It is not guaranteed that every batch will be the same size, but rather that the batches will be _no larger_ than the specified size.
   *
   * Warning: This is a potentially extremely-costly operation! It can take 10+ seconds on large installs and/or slow devices.
   * Be smart about where you call this.
   */
  fun rebuildIndex(batchSize: Long = 10_000L) {
    val maxId: Long = SignalDatabase.messages.getNextId()

    if (!SqlUtil.tableExists(readableDatabase, FTS_TABLE_NAME)) {
      Log.w(TAG, "FTS table does not exist. Rebuilding.")
      fullyResetTables()
      return
    }

    Log.i(TAG, "Re-indexing. Operating on ID's 1-$maxId in steps of $batchSize.")

    for (i in 1..maxId step batchSize) {
      Log.i(TAG, "Reindexing ID's [$i, ${i + batchSize})")
      writableDatabase.execSQL(
        """
        INSERT INTO $FTS_TABLE_NAME ($ID, $BODY) 
            SELECT 
              ${MessageTable.ID}, 
              ${MessageTable.BODY}
            FROM 
              ${MessageTable.TABLE_NAME} 
            WHERE 
              ${MessageTable.ID} >= $i AND
              ${MessageTable.ID} < ${i + batchSize}
        """
      )
    }
  }

  /**
   * Drops all tables and recreates them.
   */
  @JvmOverloads
  fun fullyResetTables(db: SQLiteDatabase = writableDatabase.sqlCipherDatabase, useTransaction: Boolean = true) {
    if (useTransaction) {
      db.beginTransaction()
    }

    try {
      Log.w(TAG, "[fullyResetTables] Dropping tables and triggers...")
      db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE_NAME")
      db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_config")
      db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_content")
      db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_data")
      db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_idx")
      db.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_INSERT")
      db.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_DELETE")
      db.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_UPDATE")

      Log.w(TAG, "[fullyResetTables] Recreating table...")
      CREATE_TABLE.forEach { db.execSQL(it) }

      Log.w(TAG, "[fullyResetTables] Recreating triggers...")
      CREATE_TRIGGERS.forEach { db.execSQL(it) }

      RebuildMessageSearchIndexJob.enqueue()

      Log.w(TAG, "[fullyResetTables] Done. Index will be rebuilt asynchronously)")

      if (useTransaction) {
        db.setTransactionSuccessful()
      }
    } finally {
      if (useTransaction) {
        db.endTransaction()
      }
    }
  }

  /**
   * We want to turn the user's query into something that works well in a MATCH query.
   * Most users expect some amount of fuzzy search, so what we do is break the string
   * into tokens, escape each token (to allow the user to search for punctuation), and
   * then append a * to the end of each token to turn it into a prefix query.
   */
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

  /**
   * If you wrap a string in quotes, sqlite considers it a string literal when making a MATCH query.
   * In order to distinguish normal quotes, you turn all " into "".
   */
  private fun fullTextSearchEscape(s: String): String {
    val quotesEscaped = s.replace("\"", "\"\"")
    return "\"$quotesEscaped\""
  }
}
