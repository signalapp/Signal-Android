package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.withinTransaction

/**
 * Keeps track of the numbers we've previously queried CDS for.
 *
 * This is important for rate-limiting: our rate-limiting strategy hinges on keeping
 * an accurate history of numbers we've queried so that we're only "charged" for
 * querying new numbers.
 */
class CdsTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(CdsTable::class.java)

    const val TABLE_NAME = "cds"

    private const val ID = "_id"
    const val E164 = "e164"
    private const val LAST_SEEN_AT = "last_seen_at"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $E164 TEXT NOT NULL UNIQUE ON CONFLICT IGNORE,
        $LAST_SEEN_AT INTEGER DEFAULT 0
      )
    """
  }

  fun getAllE164s(): Set<String> {
    val e164s: MutableSet<String> = mutableSetOf()

    readableDatabase
      .select(E164)
      .from(TABLE_NAME)
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          e164s += cursor.requireNonNullString(E164)
        }
      }

    return e164s
  }

  /**
   * Saves the set of e164s used after a full refresh.
   * @param fullE164s All of the e164s used in the last CDS query (previous and new).
   * @param seenE164s The E164s that were seen in either the system contacts or recipients table. This is different from [fullE164s] in that [fullE164s]
   *                  includes every number we've ever seen, even if it's not in our contacts anymore.
   */
  fun updateAfterFullCdsQuery(fullE164s: Set<String>, seenE164s: Set<String>) {
    val lastSeen = System.currentTimeMillis()

    writableDatabase.withinTransaction { db ->
      val existingE164s: Set<String> = getAllE164s()
      val removedE164s: Set<String> = existingE164s - fullE164s
      val addedE164s: Set<String> = fullE164s - existingE164s

      if (removedE164s.isNotEmpty()) {
        SqlUtil.buildCollectionQuery(E164, removedE164s)
          .forEach { db.delete(TABLE_NAME, it.where, it.whereArgs) }
      }

      if (addedE164s.isNotEmpty()) {
        val insertValues: List<ContentValues> = addedE164s.map { contentValuesOf(E164 to it) }

        SqlUtil.buildBulkInsert(TABLE_NAME, arrayOf(E164), insertValues)
          .forEach { db.execSQL(it.where, it.whereArgs) }
      }

      if (seenE164s.isNotEmpty()) {
        val contentValues = contentValuesOf(LAST_SEEN_AT to lastSeen)

        SqlUtil.buildCollectionQuery(E164, seenE164s)
          .forEach { query -> db.update(TABLE_NAME, contentValues, query.where, query.whereArgs) }
      }
    }
  }

  /**
   * Updates after a partial CDS query. Will not insert new entries. Instead, this will simply update the lastSeen timestamp of any entry we already have.
   * @param seenE164s The newly-added E164s that we hadn't previously queried for.
   */
  fun updateAfterPartialCdsQuery(seenE164s: Set<String>) {
    val lastSeen = System.currentTimeMillis()

    writableDatabase.withinTransaction { db ->
      val contentValues = contentValuesOf(LAST_SEEN_AT to lastSeen)

      SqlUtil.buildCollectionQuery(E164, seenE164s)
        .forEach { query -> db.update(TABLE_NAME, contentValues, query.where, query.whereArgs) }
    }
  }

  /**
   * Wipes the entire table.
   */
  fun clearAll() {
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }
}
