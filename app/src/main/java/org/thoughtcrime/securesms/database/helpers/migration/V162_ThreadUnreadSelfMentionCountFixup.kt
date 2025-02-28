package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations

/**
 * A bad cherry-pick for a database change requires us to attempt to alter the table again
 * to fix it.
 */
@Suppress("ClassName")
object V162_ThreadUnreadSelfMentionCountFixup : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (columnMissing(db, "thread", "unread_self_mention_count")) {
      Log.i(SignalDatabaseMigrations.TAG, "Fixing up thread table and unread_self_mention_count column")
      db.execSQL("ALTER TABLE thread ADD COLUMN unread_self_mention_count INTEGER DEFAULT 0")
    }
  }

  @Suppress("SameParameterValue")
  private fun columnMissing(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info($table)", arrayOf()).use { cursor ->
      val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
      while (cursor.moveToNext()) {
        val name = cursor.getString(nameColumnIndex)
        if (name == column) {
          return false
        }
      }
    }
    return true
  }
}
