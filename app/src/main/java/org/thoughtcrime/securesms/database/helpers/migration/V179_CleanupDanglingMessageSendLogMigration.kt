package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * This cleans up some MSL entries that we left behind during a bad past migration.
 */
@Suppress("ClassName")
object V179_CleanupDanglingMessageSendLogMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DELETE FROM msl_message WHERE payload_id NOT IN (SELECT _id FROM msl_payload)")
    db.execSQL("DELETE FROM msl_recipient WHERE payload_id NOT IN (SELECT _id FROM msl_payload)")
  }
}
