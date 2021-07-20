package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.model.LogEntry
import org.thoughtcrime.securesms.util.CursorUtil
import org.thoughtcrime.securesms.util.SqlUtil
import java.io.Closeable

/**
 * Stores logs.
 *
 * Logs are very performance-critical, particularly inserts and deleting old entries.
 *
 * This is it's own separate physical database, so it cannot do joins or queries with any other
 * tables.
 */
class LogDatabase private constructor(
  application: Application,
  private val databaseSecret: DatabaseSecret
) : SQLiteOpenHelper(
    application,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
    SqlCipherDatabaseHook(),
    SqlCipherErrorHandler(DATABASE_NAME)
  ),
  SignalDatabase {

  companion object {
    private val TAG = Log.tag(LogDatabase::class.java)

    private const val DATABASE_VERSION = 1
    private const val DATABASE_NAME = "signal-logs.db"

    private const val TABLE_NAME = "log"
    private const val ID = "_id"
    private const val CREATED_AT = "created_at"
    private const val EXPIRES_AT = "expires_at"
    private const val BODY = "body"
    private const val SIZE = "size"

    private val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CREATED_AT INTEGER, 
        $EXPIRES_AT INTEGER,
        $BODY TEXT, 
        $SIZE INTEGER
      )
    """.trimIndent()

    private val CREATE_INDEXES = arrayOf(
      "CREATE INDEX log_expires_at_index ON $TABLE_NAME ($EXPIRES_AT)"
    )

    @SuppressLint("StaticFieldLeak") // We hold an Application context, not a view context
    @Volatile
    private var instance: LogDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): LogDatabase {
      if (instance == null) {
        synchronized(LogDatabase::class.java) {
          if (instance == null) {
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
      db.delete(TABLE_NAME, "$EXPIRES_AT < ?", SqlUtil.buildArgs(currentTime))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getAllBeforeTime(time: Long): Reader {
    return Reader(readableDatabase.query(TABLE_NAME, arrayOf(BODY), "$CREATED_AT < ?", SqlUtil.buildArgs(time), null, null, null))
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

  fun getLogCountBeforeTime(time: Long): Int {
    readableDatabase.query(TABLE_NAME, arrayOf("COUNT(*)"), "$CREATED_AT < ?", SqlUtil.buildArgs(time), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0)
      } else {
        0
      }
    }
  }

  private fun buildValues(log: LogEntry): ContentValues {
    return ContentValues().apply {
      put(CREATED_AT, log.createdAt)
      put(EXPIRES_AT, log.createdAt + log.lifespan)
      put(BODY, log.body)
      put(SIZE, log.body.length)
    }
  }

  private val readableDatabase: SQLiteDatabase
    get() = getReadableDatabase(databaseSecret.asString())

  private val writableDatabase: SQLiteDatabase
    get() = getWritableDatabase(databaseSecret.asString())

  class Reader(private val cursor: Cursor) : Iterator<String>, Closeable {
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
