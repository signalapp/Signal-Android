package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column to track when a thread last had an unread reminder sent.
 */
@Suppress("ClassName")
object V327_AddLastUnreadReminderColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE thread ADD COLUMN last_unread_reminder INTEGER DEFAULT 0")
    db.execSQL("UPDATE thread SET last_unread_reminder = last_seen")
  }
}
