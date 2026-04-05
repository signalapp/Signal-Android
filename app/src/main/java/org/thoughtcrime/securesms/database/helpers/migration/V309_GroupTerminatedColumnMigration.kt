package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds 'terminated_by' that stores the recipient id of the
 * admin who terminated the group, -1 if unknown, 0 if not terminated.
 */
@Suppress("ClassName")
object V309_GroupTerminatedColumnMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE groups ADD COLUMN terminated_by INTEGER DEFAULT 0")
  }
}
