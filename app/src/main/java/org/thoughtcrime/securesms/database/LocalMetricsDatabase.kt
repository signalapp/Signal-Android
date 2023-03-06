package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.model.LocalMetricsEvent
import java.util.concurrent.TimeUnit

/**
 * Stores metrics for user events locally on disk.
 *
 * These metrics are only ever included in debug logs in an aggregate fashion (i.e. p50, p90, p99) and are never automatically uploaded anywhere.
 *
 * The performance of insertions is important, but given insertion frequency isn't crazy-high, we can also optimize for retrieval performance.
 * SQLite isn't amazing at statistical analysis, so having indices that speed up those operations is encouraged.
 *
 * This is it's own separate physical database, so it cannot do joins or queries with any other tables.
 */
class LocalMetricsDatabase private constructor(
  application: Application,
  databaseSecret: DatabaseSecret
) :
  SQLiteOpenHelper(
    application,
    DATABASE_NAME,
    databaseSecret.asString(),
    null,
    DATABASE_VERSION,
    0,
    SqlCipherDeletingErrorHandler(DATABASE_NAME),
    SqlCipherDatabaseHook(),
    true
  ),
  SignalDatabaseOpenHelper {

  companion object {
    private val TAG = Log.tag(LocalMetricsDatabase::class.java)

    private val MAX_AGE = TimeUnit.DAYS.toMillis(7)

    private const val DATABASE_VERSION = 1
    private const val DATABASE_NAME = "signal-local-metrics.db"

    private const val TABLE_NAME = "events"
    private const val ID = "_id"
    private const val CREATED_AT = "created_at"
    private const val EVENT_ID = "event_id"
    private const val EVENT_NAME = "event_name"
    private const val SPLIT_NAME = "split_name"
    private const val DURATION = "duration"

    private val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CREATED_AT INTEGER NOT NULL, 
        $EVENT_ID TEXT NOT NULL,
        $EVENT_NAME TEXT NOT NULL,
        $SPLIT_NAME TEXT NOT NULL,
        $DURATION INTEGER NOT NULL
      )
    """.trimIndent()

    private val CREATE_INDEXES = arrayOf(
      "CREATE INDEX events_create_at_index ON $TABLE_NAME ($CREATED_AT)",
      "CREATE INDEX events_event_name_split_name_index ON $TABLE_NAME ($EVENT_NAME, $SPLIT_NAME)",
      "CREATE INDEX events_duration_index ON $TABLE_NAME ($DURATION)"
    )

    @SuppressLint("StaticFieldLeak") // We hold an Application context, not a view context
    @Volatile
    private var instance: LocalMetricsDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): LocalMetricsDatabase {
      if (instance == null) {
        synchronized(LocalMetricsDatabase::class.java) {
          if (instance == null) {
            SqlCipherLibraryLoader.load()
            instance = LocalMetricsDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context))
          }
        }
      }
      return instance!!
    }
  }

  private object EventTotals {
    const val VIEW_NAME = "event_totals"

    val CREATE_VIEW = """
      CREATE VIEW $VIEW_NAME AS
        SELECT $EVENT_ID, $EVENT_NAME, SUM($DURATION) AS $DURATION
        FROM $TABLE_NAME
        GROUP BY $EVENT_ID
    """.trimIndent()
  }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")

    db.execSQL(CREATE_TABLE)
    CREATE_INDEXES.forEach { db.execSQL(it) }

    db.execSQL(EventTotals.CREATE_VIEW)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  }

  override fun onOpen(db: SQLiteDatabase) {
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }

  fun insert(currentTime: Long, event: LocalMetricsEvent) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      event.splits.forEach { split ->
        db.insert(
          TABLE_NAME,
          null,
          ContentValues().apply {
            put(CREATED_AT, event.createdAt)
            put(EVENT_ID, event.eventId)
            put(EVENT_NAME, event.eventName)
            put(SPLIT_NAME, split.name)
            put(DURATION, split.duration)
          }
        )
      }

      db.delete(TABLE_NAME, "$CREATED_AT < ?", SqlUtil.buildArgs(currentTime - MAX_AGE))

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun clear() {
    writableDatabase.delete(TABLE_NAME, null, null)
  }

  fun getMetrics(): List<EventMetrics> {
    val db = readableDatabase

    db.beginTransaction()
    try {
      val events: Map<String, List<String>> = getUniqueEventNames()

      val metrics: List<EventMetrics> = events.map { (eventName: String, splits: List<String>) ->
        EventMetrics(
          name = eventName,
          count = getCount(eventName),
          p50 = eventPercent(eventName, 50),
          p90 = eventPercent(eventName, 90),
          p99 = eventPercent(eventName, 99),
          splits = splits.map { splitName ->
            SplitMetrics(
              name = splitName,
              p50 = splitPercent(eventName, splitName, 50),
              p90 = splitPercent(eventName, splitName, 90),
              p99 = splitPercent(eventName, splitName, 99)
            )
          }
        )
      }

      db.setTransactionSuccessful()

      return metrics
    } finally {
      db.endTransaction()
    }
  }

  private fun getUniqueEventNames(): Map<String, List<String>> {
    val events = mutableMapOf<String, MutableList<String>>()

    readableDatabase.rawQuery("SELECT DISTINCT $EVENT_NAME, $SPLIT_NAME FROM $TABLE_NAME", null).use { cursor ->
      while (cursor.moveToNext()) {
        val eventName = CursorUtil.requireString(cursor, EVENT_NAME)
        val splitName = CursorUtil.requireString(cursor, SPLIT_NAME)

        events.getOrPut(eventName) {
          mutableListOf()
        }.add(splitName)
      }
    }

    return events
  }

  private fun getCount(eventName: String): Long {
    readableDatabase.rawQuery("SELECT COUNT(DISTINCT $EVENT_ID) FROM $TABLE_NAME WHERE $EVENT_NAME = ?", SqlUtil.buildArgs(eventName)).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getLong(0)
      } else {
        0
      }
    }
  }

  private fun eventPercent(eventName: String, percent: Int): Long {
    return percentile(EventTotals.VIEW_NAME, "$EVENT_NAME = '$eventName'", percent)
  }

  private fun splitPercent(eventName: String, splitName: String, percent: Int): Long {
    return percentile(TABLE_NAME, "$EVENT_NAME = '$eventName' AND $SPLIT_NAME = '$splitName'", percent)
  }

  private fun percentile(table: String, where: String, percent: Int): Long {
    val query: String = """
      SELECT $DURATION
      FROM $table
      WHERE $where
      ORDER BY $DURATION ASC
      LIMIT 1
      OFFSET (SELECT COUNT(*)
              FROM $table
              WHERE $where) * $percent / 100 - 1
    """.trimIndent()

    readableDatabase.rawQuery(query, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getLong(0)
      } else {
        -1
      }
    }
  }

  data class EventMetrics(
    val name: String,
    val count: Long,
    val p50: Long,
    val p90: Long,
    val p99: Long,
    val splits: List<SplitMetrics>
  )

  data class SplitMetrics(
    val name: String,
    val p50: Long,
    val p90: Long,
    val p99: Long
  )
}
