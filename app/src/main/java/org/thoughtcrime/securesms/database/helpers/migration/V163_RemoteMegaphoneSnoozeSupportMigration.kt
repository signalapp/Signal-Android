package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Add columns needed to track remote megaphone specific snooze rates.
 */
object V163_RemoteMegaphoneSnoozeSupportMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (columnMissing(db, "primary_action_data")) {
      db.execSQL("ALTER TABLE remote_megaphone ADD COLUMN primary_action_data TEXT DEFAULT NULL")
    }

    if (columnMissing(db, "secondary_action_data")) {
      db.execSQL("ALTER TABLE remote_megaphone ADD COLUMN secondary_action_data TEXT DEFAULT NULL")
    }

    if (columnMissing(db, "snoozed_at")) {
      db.execSQL("ALTER TABLE remote_megaphone ADD COLUMN snoozed_at INTEGER DEFAULT 0")
    }

    if (columnMissing(db, "seen_count")) {
      db.execSQL("ALTER TABLE remote_megaphone ADD COLUMN seen_count INTEGER DEFAULT 0")
    }
  }

  private fun columnMissing(db: SupportSQLiteDatabase, column: String): Boolean {
    db.query("PRAGMA table_info(remote_megaphone)", null).use { cursor ->
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
