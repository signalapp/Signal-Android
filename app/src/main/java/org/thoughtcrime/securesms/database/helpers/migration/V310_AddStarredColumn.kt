package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column for tracking the starred status of a message.
 */
@Suppress("ClassName")
object V310_AddStarredColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN starred INTEGER DEFAULT 0")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_starred_index ON message (starred) WHERE starred > 0")
  }
}
