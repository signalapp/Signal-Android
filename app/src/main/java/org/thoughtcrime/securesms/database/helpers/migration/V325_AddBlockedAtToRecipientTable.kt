package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column to track when a recipient was blocked.
 * 0 means we don't know the time they were blocked (eg could be unblocked, or blocked prior to us storing it)
 */
@Suppress("ClassName")
object V325_AddBlockedAtToRecipientTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN blocked_at INTEGER DEFAULT 0")
  }
}
