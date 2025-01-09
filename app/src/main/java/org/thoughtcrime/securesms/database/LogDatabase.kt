package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.app.Application
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.exists
import org.signal.core.util.getTableRowCount
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.crash.CrashConfig
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.model.LogEntry
import java.io.Closeable
import kotlin.math.abs
import kotlin.time.Duration.Companion.days

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

    private const val DATABASE_VERSION = 4
    private const val DATABASE_NAME = "signal-logs.db"

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

  @get:JvmName("logs")
  val logs: LogTable by lazy { LogTable(this) }

  @get:JvmName("crashes")
  val crashes: CrashTable by lazy { CrashTable(this) }

  @get:JvmName("anrs")
  val anrs: AnrTable by lazy { AnrTable(this) }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")

    db.execSQL(LogTable.CREATE_TABLE)
    db.execSQL(CrashTable.CREATE_TABLE)
    db.execSQL(AnrTable.CREATE_TABLE)

    LogTable.CREATE_INDEXES.forEach { db.execSQL(it) }
    CrashTable.CREATE_INDEXES.forEach { db.execSQL(it) }
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")

    if (oldVersion < 2) {
      db.execSQL("DROP TABLE log")
      db.execSQL("CREATE TABLE log (_id INTEGER PRIMARY KEY, created_at INTEGER, keep_longer INTEGER DEFAULT 0, body TEXT, size INTEGER)")
      db.execSQL("CREATE INDEX keep_longer_index ON log (keep_longer)")
      db.execSQL("CREATE INDEX log_created_at_keep_longer_index ON log (created_at, keep_longer)")
    }

    if (oldVersion < 3) {
      db.execSQL("CREATE TABLE crash (_id INTEGER PRIMARY KEY, created_at INTEGER, name TEXT, message TEXT, stack_trace TEXT, last_prompted_at INTEGER)")
      db.execSQL("CREATE INDEX crash_created_at ON crash (created_at)")
      db.execSQL("CREATE INDEX crash_name_message ON crash (name, message)")
    }

    if (oldVersion < 4) {
      db.execSQL("CREATE TABLE anr (_id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, thread_dump TEXT NOT NULL)")
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }

  class LogTable(private val openHelper: LogDatabase) {
    companion object {
      const val TABLE_NAME = "log"
      const val ID = "_id"
      const val CREATED_AT = "created_at"
      const val KEEP_LONGER = "keep_longer"
      const val BODY = "body"
      const val SIZE = "size"

      const val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
          $ID INTEGER PRIMARY KEY,
          $CREATED_AT INTEGER, 
          $KEEP_LONGER INTEGER DEFAULT 0,
          $BODY TEXT,
          $SIZE INTEGER
        )
      """

      val CREATE_INDEXES = arrayOf(
        "CREATE INDEX keep_longer_index ON $TABLE_NAME ($KEEP_LONGER)",
        "CREATE INDEX log_created_at_keep_longer_index ON $TABLE_NAME ($CREATED_AT, $KEEP_LONGER)"
      )

      val MAX_FILE_SIZE = 20L.mebiBytes.inWholeBytes
      val DEFAULT_LIFESPAN = 3.days.inWholeMilliseconds
      val LONGER_LIFESPAN = 21.days.inWholeMilliseconds
    }

    private val readableDatabase: SQLiteDatabase get() = openHelper.readableDatabase
    private val writableDatabase: SQLiteDatabase get() = openHelper.writableDatabase

    fun insert(logs: List<LogEntry>, currentTime: Long) {
      writableDatabase.withinTransaction { db ->
        logs.forEach { log ->
          db.insertInto(TABLE_NAME)
            .values(
              CREATED_AT to log.createdAt,
              KEEP_LONGER to if (log.keepLonger) 1 else 0,
              BODY to log.body,
              SIZE to log.body.length
            )
            .run()
        }

        db.delete(TABLE_NAME)
          .where("($CREATED_AT < ? AND $KEEP_LONGER = 0) OR ($CREATED_AT < ? AND $KEEP_LONGER = 1)", currentTime - DEFAULT_LIFESPAN, currentTime - LONGER_LIFESPAN)
          .run()
      }
    }

    fun getAllBeforeTime(time: Long): Reader {
      return readableDatabase
        .select(BODY)
        .from(TABLE_NAME)
        .where("$CREATED_AT < $time")
        .run()
        .toReader()
    }

    fun getRangeBeforeTime(start: Int, length: Int, time: Long): List<String> {
      return readableDatabase
        .select(BODY)
        .from(TABLE_NAME)
        .where("$CREATED_AT < $time")
        .limit(limit = length, offset = start)
        .run()
        .readToList { it.requireNonNullString(BODY) }
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
          writableDatabase
            .delete(TABLE_NAME)
            .where("$KEEP_LONGER = 0")
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
      return readableDatabase
        .select("COUNT(*)")
        .from(TABLE_NAME)
        .where("$CREATED_AT < $time")
        .run()
        .readToSingleInt()
    }

    fun clearKeepLonger() {
      writableDatabase
        .delete(TABLE_NAME)
        .where("$KEEP_LONGER = 1")
        .run()
    }

    fun clearAll() {
      writableDatabase.deleteAll(TABLE_NAME)
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

    private fun Cursor.toReader(): CursorReader {
      return CursorReader(this)
    }

    interface Reader : Iterator<String>, Closeable

    class CursorReader(private val cursor: Cursor) : Reader {
      override fun hasNext(): Boolean {
        return !cursor.isLast && cursor.count > 0
      }

      override fun next(): String {
        cursor.moveToNext()
        return CursorUtil.requireString(cursor, LogTable.BODY)
      }

      override fun close() {
        cursor.close()
      }
    }
  }

  class CrashTable(private val openHelper: LogDatabase) {
    companion object {
      const val TABLE_NAME = "crash"
      const val ID = "_id"
      const val CREATED_AT = "created_at"
      const val NAME = "name"
      const val MESSAGE = "message"
      const val STACK_TRACE = "stack_trace"
      const val LAST_PROMPTED_AT = "last_prompted_at"

      const val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
          $ID INTEGER PRIMARY KEY,
          $CREATED_AT INTEGER,
          $NAME TEXT,
          $MESSAGE TEXT,
          $STACK_TRACE TEXT,
          $LAST_PROMPTED_AT INTEGER
        )
      """

      val CREATE_INDEXES = arrayOf(
        "CREATE INDEX crash_created_at ON $TABLE_NAME ($CREATED_AT)",
        "CREATE INDEX crash_name_message ON $TABLE_NAME ($NAME, $MESSAGE)"
      )
    }

    private val readableDatabase: SQLiteDatabase get() = openHelper.readableDatabase
    private val writableDatabase: SQLiteDatabase get() = openHelper.writableDatabase

    fun saveCrash(createdAt: Long, name: String, message: String?, stackTrace: String) {
      writableDatabase
        .insertInto(TABLE_NAME)
        .values(
          CREATED_AT to createdAt,
          NAME to name,
          MESSAGE to message,
          STACK_TRACE to stackTrace,
          LAST_PROMPTED_AT to 0
        )
        .run()

      trimToSize()
    }

    /**
     * Returns true if crashes exists that
     * (1) match any of the provided crash patterns
     * (2) have not been prompted within the [promptThreshold]
     */
    fun anyMatch(patterns: Collection<CrashConfig.CrashPattern>, promptThreshold: Long): Boolean {
      for (pattern in patterns) {
        val (query, args) = pattern.asLikeQuery()

        val found = readableDatabase
          .exists(TABLE_NAME)
          .where("$query AND $LAST_PROMPTED_AT < $promptThreshold", args)
          .run()

        if (found) {
          return true
        }
      }

      return false
    }

    /**
     * Marks all crashes that match any of the provided patterns as being prompted at the provided [promptedAt] time.
     */
    fun markAsPrompted(patterns: Collection<CrashConfig.CrashPattern>, promptedAt: Long) {
      for (pattern in patterns) {
        val (query, args) = pattern.asLikeQuery()

        readableDatabase
          .update(TABLE_NAME)
          .values(LAST_PROMPTED_AT to promptedAt)
          .where(query, args)
          .run()
      }
    }

    fun trimToSize() {
      // Delete crashes older than 30 days
      val threshold = System.currentTimeMillis() - 30.days.inWholeMilliseconds
      writableDatabase
        .delete(TABLE_NAME)
        .where("$CREATED_AT < $threshold")
        .run()

      // Only keep 100 most recent crashes to prevent crash loops from filling up the disk
      writableDatabase
        .delete(TABLE_NAME)
        .where("$ID NOT IN (SELECT $ID FROM $TABLE_NAME ORDER BY $CREATED_AT DESC LIMIT 100)")
        .run()
    }

    fun clear() {
      writableDatabase.deleteAll(TABLE_NAME)
    }

    private fun CrashConfig.CrashPattern.asLikeQuery(): Pair<String, Array<String>> {
      val query = StringBuilder()
      var args = arrayOf<String>()

      if (namePattern != null) {
        query.append("$NAME LIKE ?")
        args += "%$namePattern%"
      }

      if (messagePattern != null) {
        if (query.isNotEmpty()) {
          query.append(" AND ")
        }
        query.append("$MESSAGE LIKE ?")
        args += "%$messagePattern%"
      }

      if (stackTracePattern != null) {
        if (query.isNotEmpty()) {
          query.append(" AND ")
        }
        query.append("$STACK_TRACE LIKE ?")
        args += "%$stackTracePattern%"
      }

      return query.toString() to args
    }
  }

  class AnrTable(private val openHelper: LogDatabase) {
    companion object {
      const val TABLE_NAME = "anr"
      const val ID = "_id"
      const val CREATED_AT = "created_at"
      const val THREAD_DUMP = "thread_dump"

      const val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
          $ID INTEGER PRIMARY KEY,
          $CREATED_AT INTEGER NOT NULL,
          $THREAD_DUMP TEXT NOT NULL
        )
      """

      val MAX_DUMP_SIZE = 1.mebiBytes.inWholeBytes.toInt()
      const val TRIMMED_FOOTER = "...\n\nTruncated because the dump exceeded 1MiB in size!"
    }

    private val readableDatabase: SQLiteDatabase get() = openHelper.readableDatabase
    private val writableDatabase: SQLiteDatabase get() = openHelper.writableDatabase

    fun save(currentTime: Long, threadDumps: String) {
      val trimmedDump = if (threadDumps.length > MAX_DUMP_SIZE) {
        Log.w(TAG, "Large ANR thread dump! Size: ${threadDumps.length}")
        threadDumps.substring(0, MAX_DUMP_SIZE - TRIMMED_FOOTER.length) + TRIMMED_FOOTER
      } else {
        threadDumps
      }

      writableDatabase
        .insertInto(TABLE_NAME)
        .values(
          CREATED_AT to currentTime,
          THREAD_DUMP to trimmedDump
        )
        .run()

      val count = writableDatabase
        .delete(TABLE_NAME)
        .where(
          """
          $ID NOT IN (SELECT $ID FROM $TABLE_NAME ORDER BY $CREATED_AT DESC LIMIT 10)
          """.trimIndent()
        )
        .run()

      if (count > 0) {
        Log.i(TAG, "Deleted $count old ANRs")
      }
    }

    fun getAll(): List<AnrRecord> {
      return readableDatabase
        .select()
        .from(TABLE_NAME)
        .run()
        .readToList { cursor ->
          AnrRecord(
            createdAt = cursor.requireLong(CREATED_AT),
            threadDump = cursor.requireNonNullString(THREAD_DUMP)
          )
        }
        .sortedBy { it.createdAt }
    }

    fun clear() {
      writableDatabase.deleteAll(TABLE_NAME)
    }

    data class AnrRecord(
      val createdAt: Long,
      val threadDump: String
    )
  }
}
