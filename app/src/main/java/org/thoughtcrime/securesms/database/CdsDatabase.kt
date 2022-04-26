package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update

/**
 * Keeps track of the numbers we've previously queried CDS for.
 *
 * This is important for rate-limiting: our rate-limiting strategy hinges on keeping
 * an accurate history of numbers we've queried so that we're only "charged" for
 * querying new numbers.
 */
class CdsDatabase(context: Context, databaseHelper: SignalDatabase) : Database(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(CdsDatabase::class.java)

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
   * @param newE164s The newly-added E164s that we hadn't previously queried for.
   * @param seenE164s The E164s that were seen in either the system contacts or recipients table.
   *                  This should be a superset of [newE164s]
   *
   */
  fun updateAfterCdsQuery(newE164s: Set<String>, seenE164s: Set<String>) {
    val lastSeen = System.currentTimeMillis()

    writableDatabase.beginTransaction()
    try {
      val insertValues: List<ContentValues> = newE164s.map { contentValuesOf(E164 to it) }

      SqlUtil.buildBulkInsert(TABLE_NAME, arrayOf(E164), insertValues)
        .forEach { writableDatabase.execSQL(it.where, it.whereArgs) }

      val contentValues = contentValuesOf(LAST_SEEN_AT to lastSeen)

      SqlUtil.buildCollectionQuery(E164, seenE164s)
        .forEach { query -> writableDatabase.update(TABLE_NAME, contentValues, query.where, query.whereArgs) }

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }
  }

  fun clearAll() {
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }
}
