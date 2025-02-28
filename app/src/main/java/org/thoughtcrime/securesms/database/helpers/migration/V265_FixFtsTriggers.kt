package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We've seen evidence of some users missing certain triggers. This migration checks for that, and if so, will completely tear down and rebuild the FTS.
 */
@Suppress("ClassName")
object V265_FixFtsTriggers : SignalDatabaseMigration {

  private val TAG = Log.tag(V265_FixFtsTriggers::class)

  private const val FTS_TABLE_NAME = "message_fts"

  private val REQUIRED_TRIGGERS = listOf(
    "message_ai",
    "message_ad",
    "message_au"
  )

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    val hasAllTriggers = REQUIRED_TRIGGERS.all { db.triggerExists(it) }
    if (hasAllTriggers) {
      Log.d(TAG, "Already have all triggers, no need for corrective action.")
      return
    }
    stopwatch.split("precheck")

    Log.w(TAG, "We're missing some triggers! Tearing everything down and rebuilding it.")

    try {
      db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE_NAME")
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to drop the message_fts table! Trying a different way.")
      db.safeDropFtsTable()
    }

    db.execSQL("DROP TRIGGER IF EXISTS message_ai")
    db.execSQL("DROP TRIGGER IF EXISTS message_ad")
    db.execSQL("DROP TRIGGER IF EXISTS message_au")
    stopwatch.split("drop")

    db.execSQL("""CREATE VIRTUAL TABLE $FTS_TABLE_NAME USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id, tokenize = "unicode61 categories 'L* N* Co Sc So'")""")

    db.execSQL("INSERT INTO message_fts(message_fts) VALUES ('rebuild')")

    db.execSQL(
      """
      CREATE TRIGGER message_ai AFTER INSERT ON message BEGIN
        INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);
      END;
    """
    )

    db.execSQL(
      """
      CREATE TRIGGER message_ad AFTER DELETE ON message BEGIN
        INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES ('delete', old._id, old.body, old.thread_id);
      END;
    """
    )

    db.execSQL(
      """
      CREATE TRIGGER message_au AFTER UPDATE ON message BEGIN
        INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);
        INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);
      END;
    """
    )

    stopwatch.split("rebuild")
    stopwatch.stop(TAG)
  }

  /**
   * Due to issues we've had in the past, the delete sequence here is very particular. It mimics the "safe drop" process in the SQLite source code
   * that prevents weird vtable constructor issues when dropping potentially-corrupt tables. https://sqlite.org/src/info/4db9258a78?ln=1549-1592
   */
  private fun SQLiteDatabase.safeDropFtsTable() {
    if (SqlUtil.tableExists(this, FTS_TABLE_NAME)) {
      val dataExists = SqlUtil.tableExists(this, "${FTS_TABLE_NAME}_data")
      val configExists = SqlUtil.tableExists(this, "${FTS_TABLE_NAME}_config")

      if (dataExists) this.execSQL("DELETE FROM ${FTS_TABLE_NAME}_data")
      if (configExists) this.execSQL("DELETE FROM ${FTS_TABLE_NAME}_config")
      if (dataExists) this.execSQL("INSERT INTO ${FTS_TABLE_NAME}_data VALUES(10, X'0000000000')")
      if (configExists) this.execSQL("INSERT INTO ${FTS_TABLE_NAME}_config VALUES('version', 4)")

      this.execSQL("DROP TABLE $FTS_TABLE_NAME")
    }
  }

  private fun SQLiteDatabase.triggerExists(tableName: String): Boolean {
    this.query("SELECT name FROM sqlite_master WHERE type=? AND name=?", arrayOf("trigger", tableName)).use { cursor ->
      return cursor.moveToFirst()
    }
  }

  private fun SQLiteDatabase.tableExists(table: String): Boolean {
    this.query("SELECT name FROM sqlite_master WHERE type=? AND name=?", arrayOf("table", table)).use { cursor ->
      return cursor.moveToFirst()
    }
  }
}
