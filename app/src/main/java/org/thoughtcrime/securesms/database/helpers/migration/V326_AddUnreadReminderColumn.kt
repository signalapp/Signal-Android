package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column to track per-conversation settings on whether to periodically send unread reminders.
 */
@Suppress("ClassName")
object V326_AddUnreadReminderColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN unread_reminder INTEGER DEFAULT 0")
  }
}
