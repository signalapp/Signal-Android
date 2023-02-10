package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.getTableRowCount
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.model.LogEntry
import org.thoughtcrime.securesms.util.ByteUnit
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Stores logs.
 *
 * Logs are very performance critical. Even though this database is written to on a low-priority background thread, we want to keep throughput high and ensure
 * that we aren't creating excess garbage.
 *
 * This is it's own separate physical database, so it cannot do joins or queries with any other tables.
 */
class LogDatabase private constructor(
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
    private val TAG = Log.tag(LogDatabase::class.java)

    private val MAX_FILE_SIZE = ByteUnit.MEGABYTES.toBytes(20)
    private val DEFAULT_LIFESPAN = TimeUnit.DAYS.toMillis(3)
    private val LONGER_LIFESPAN = TimeUnit.DAYS.toMillis(21)

    private const val DATABASE_VERSION = 2
    private const val DATABASE_NAME = "signal-logs.db"

    private const val TABLE_NAME = "log"
    private const val ID = "_id"
    private const val CREATED_AT = "created_at"
    private const val KEEP_LONGER = "keep_longer"
    private const val BODY = "body"
    private const val SIZE = "size"

    private val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CREATED_AT INTEGER, 
        $KEEP_LONGER INTEGER DEFAULT 0,
        $BODY TEXT,
        $SIZE INTEGER
      )
    """.trimIndent()

    private val CREATE_INDEXES = arrayOf(
      "CREATE INDEX keep_longer_index ON $TABLE_NAME ($KEEP_LONGER)",
      "CREATE INDEX log_created_at_keep_longer_index ON $TABLE_NAME ($CREATED_AT, $KEEP_LONGER)"
    )

    @SuppressLint("StaticFieldLeak") // We hold an Application context, not a view context
    @Volatile
    private var instance: LogDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): LogDatabase {
      if (instance == null) {
        synchronized(LogDatabase::class.java) {
          if (instance == null) {
            SqlCipherLibraryLoader.load()
            instance = LogDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context))
          }
        }
      }
      return instance!!
    }
  }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")
    db.execSQL(CREATE_TABLE)
    CREATE_INDEXES.forEach { db.execSQL(it) }
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")

    if (oldVersion < 2) {
      db.execSQL("DROP TABLE log")
      db.execSQL("CREATE TABLE log (_id INTEGER PRIMARY KEY, created_at INTEGER, keep_longer INTEGER DEFAULT 0, body TEXT, size INTEGER)")
      db.execSQL("CREATE INDEX keep_longer_index ON log (keep_longer)")
      db.execSQL("CREATE INDEX log_created_at_keep_longer_index ON log (created_at, keep_longer)")
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }

  fun insert(logs: List<LogEntry>, currentTime: Long) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      logs.forEach { log ->
        db.insert(TABLE_NAME, null, buildValues(log))
      }

      db.delete(
        TABLE_NAME,
        "($CREATED_AT < ? AND $KEEP_LONGER = ?) OR ($CREATED_AT < ? AND $KEEP_LONGER = ?)",
        SqlUtil.buildArgs(currentTime - DEFAULT_LIFESPAN, 0, currentTime - LONGER_LIFESPAN, 1)
      )

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getAllBeforeTime(time: Long): Reader {
    return CursorReader(readableDatabase.query(TABLE_NAME, arrayOf(BODY), "$CREATED_AT < ?", SqlUtil.buildArgs(time), null, null, null))
  }

  fun getRangeBeforeTime(start: Int, length: Int, time: Long): List<String> {
    val lines = mutableListOf<String>()

    readableDatabase.query(TABLE_NAME, arrayOf(BODY), "$CREATED_AT < ?", SqlUtil.buildArgs(time), null, null, null, "$start,$length").use { cursor ->
      while (cursor.moveToNext()) {
        lines.add(CursorUtil.requireString(cursor, BODY))
      }
    }

    return lines
  }

  fun trimToSize() {
    val currentTime = System.currentTimeMillis()
    val stopwatch = Stopwatch("trim")

    val sizeOfSpecialLogs: Long = getSize("$KEEP_LONGER = ?", arrayOf("1"))
    val remainingSize = MAX_FILE_SIZE - sizeOfSpecialLogs

    stopwatch.split("keepers-size")

    if (remainingSize <= 0) {
      if (abs(remainingSize) > MAX_FILE_SIZE / 2) {
        // Not only are KEEP_LONGER logs putting us over the storage limit, it's doing it by a lot! Delete half.
        val logCount = readableDatabase.getTableRowCount(TABLE_NAME)
        writableDatabase.execSQL("DELETE FROM $TABLE_NAME WHERE $ID < (SELECT MAX($ID) FROM (SELECT $ID FROM $TABLE_NAME LIMIT ${logCount / 2}))")
      } else {
        writableDatabase.delete(TABLE_NAME, "$KEEP_LONGER = ?", arrayOf("0"))
      }
      return
    }

    val sizeDiffThreshold = MAX_FILE_SIZE * 0.01

    var lhs: Long = currentTime - DEFAULT_LIFESPAN
    var rhs: Long = currentTime
    var mid: Long = 0
    var sizeOfChunk: Long

    while (lhs < rhs - 2) {
      mid = (lhs + rhs) / 2
      sizeOfChunk = getSize("$CREATED_AT > ? AND $CREATED_AT < ? AND $KEEP_LONGER = ?", SqlUtil.buildArgs(mid, currentTime, 0))

      if (sizeOfChunk > remainingSize) {
        lhs = mid
      } else if (sizeOfChunk < remainingSize) {
        if (remainingSize - sizeOfChunk < sizeDiffThreshold) {
          break
        } else {
          rhs = mid
        }
      } else {
        break
      }
    }

    stopwatch.split("binary-search")

    writableDatabase.delete(TABLE_NAME, "$CREATED_AT < ? AND $KEEP_LONGER = ?", SqlUtil.buildArgs(mid, 0))

    stopwatch.split("delete")
    stopwatch.stop(TAG)
  }

  fun getLogCountBeforeTime(time: Long): Int {
    readableDatabase.query(TABLE_NAME, arrayOf("COUNT(*)"), "$CREATED_AT < ?", SqlUtil.buildArgs(time), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0)
      } else {
        0
      }
    }
  }

  fun clearKeepLonger() {
    writableDatabase.delete(TABLE_NAME)
      .where("$KEEP_LONGER = ?", 1)
      .run()
  }

  private fun buildValues(log: LogEntry): ContentValues {
    return ContentValues().apply {
      put(CREATED_AT, log.createdAt)
      put(KEEP_LONGER, if (log.keepLonger) 1 else 0)
      put(BODY, log.body)
      put(SIZE, log.body.length)
    }
  }

  private fun getSize(query: String?, args: Array<String>?): Long {
    readableDatabase.query(TABLE_NAME, arrayOf("SUM($SIZE)"), query, args, null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getLong(0)
      } else {
        0
      }
    }
  }

  interface Reader : Iterator<String>, Closeable

  class CursorReader(private val cursor: Cursor) : Reader {
    override fun hasNext(): Boolean {
      return !cursor.isLast && cursor.count > 0
    }

    override fun next(): String {
      cursor.moveToNext()
      return CursorUtil.requireString(cursor, BODY)
    }

    override fun close() {
      cursor.close()
    }
  }
}
