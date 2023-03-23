package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import org.intellij.lang.annotations.Language
import org.signal.core.util.SqlUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
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
      "CREATE VIRTUAL TABLE $FTS_TABLE_NAME USING fts5($BODY, $THREAD_ID UNINDEXED, content=${MessageTable.TABLE_NAME}, content_rowid=${MessageTable.ID})"
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
        ${MessageTable.TABLE_NAME}.${MessageTable.RECIPIENT_ID} AS $MESSAGE_RECIPIENT, 
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
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE} & ${MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION} = 0 
      ORDER BY ${MessageTable.DATE_RECEIVED} DESC 
      LIMIT 500
    """

    @Language("sql")
    private const val MESSAGES_FOR_THREAD_QUERY = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} AS $CONVERSATION_RECIPIENT, 
        ${MessageTable.TABLE_NAME}.${MessageTable.RECIPIENT_ID} AS $MESSAGE_RECIPIENT,
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
        ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = ? 
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
        """.trimIndent()
      )
    }
  }

  /**
   * This performs the same thing as the `optimize` command in SQLite, but broken into iterative stages to avoid locking up the database for too long.
   * If what's going on in this method seems weird, that's because it is, but please read the sqlite docs -- we're following their algorithm:
   * https://www.sqlite.org/fts5.html#the_optimize_command
   *
   * Note that in order for the [SqlUtil.getTotalChanges] call to work, we have to be within a transaction, or else the connection pool screws everything up
   * (the stats are on a per-connection basis).
   *
   * There's this double-batching mechanism happening here to strike a balance between making individual transactions short while also not hammering the
   * database with a ton of independent transactions.
   *
   * To give you some ballpark numbers, on a large database (~400k messages), it takes ~75 iterations to fully optimize everything.
   */
  fun optimizeIndex(timeout: Long): Boolean {
    val pageSize = 64 // chosen through experimentation
    val batchSize = 10 // chosen through experimentation
    val noChangeThreshold = 2 // if less changes occurred than this, operation is considered no-op (see sqlite docs ref'd in kdoc)

    val startTime = System.currentTimeMillis()
    var totalIterations = 0
    var totalBatches = 0
    var actualWorkTime = 0L
    var finished = false

    while (!finished) {
      var batchIterations = 0
      val batchStartTime = System.currentTimeMillis()

      writableDatabase.withinTransaction { db ->
        // Note the negative page size -- see sqlite docs ref'd in kdoc
        db.execSQL("INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) values ('merge', -$pageSize)")
        var previousCount = SqlUtil.getTotalChanges(db)

        val iterativeStatement = db.compileStatement("INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) values ('merge', $pageSize)")
        iterativeStatement.execute()
        var count = SqlUtil.getTotalChanges(db)

        while (batchIterations < batchSize && count - previousCount >= noChangeThreshold) {
          previousCount = count
          iterativeStatement.execute()

          count = SqlUtil.getTotalChanges(db)
          batchIterations++
        }

        if (count - previousCount < noChangeThreshold) {
          finished = true
        }
      }

      totalIterations += batchIterations
      totalBatches++
      actualWorkTime += System.currentTimeMillis() - batchStartTime

      if (actualWorkTime >= timeout) {
        Log.w(TAG, "Timed out during optimization! We did $totalIterations iterations across $totalBatches batches, taking ${System.currentTimeMillis() - startTime} ms. Bailed out to avoid database lockup.")
        return false
      }

      // We want to sleep in between batches to give other db operations a chance to run
      ThreadUtil.sleep(50)
    }

    Log.d(TAG, "Took ${System.currentTimeMillis() - startTime} ms and $totalIterations iterations across $totalBatches batches to optimize. Of that time, $actualWorkTime ms were spent actually working (~${actualWorkTime / totalBatches} ms/batch). The rest was spent sleeping.")
    return true
  }

  /**
   * Drops all tables and recreates them.
   */
  fun fullyResetTables() {
    Log.w(TAG, "[fullyResetTables] Dropping tables and triggers...")
    writableDatabase.execSQL("DROP TABLE IF EXISTS $FTS_TABLE_NAME")
    writableDatabase.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_config")
    writableDatabase.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_content")
    writableDatabase.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_data")
    writableDatabase.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_idx")
    writableDatabase.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_INSERT")
    writableDatabase.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_DELETE")
    writableDatabase.execSQL("DROP TRIGGER IF EXISTS $TRIGGER_AFTER_UPDATE")

    Log.w(TAG, "[fullyResetTables] Recreating table...")
    CREATE_TABLE.forEach { writableDatabase.execSQL(it) }

    Log.w(TAG, "[fullyResetTables] Recreating triggers...")
    CREATE_TRIGGERS.forEach { writableDatabase.execSQL(it) }

    RebuildMessageSearchIndexJob.enqueue()

    Log.w(TAG, "[fullyResetTables] Done. Index will be rebuilt asynchronously)")
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
