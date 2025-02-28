package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds necessary book-keeping columns to SMS and MMS tables for SMS export.
 */
@Suppress("ClassName")
object V155_SmsExporterMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE mms ADD COLUMN export_state BLOB DEFAULT NULL")
    db.execSQL("ALTER TABLE mms ADD COLUMN exported INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sms ADD COLUMN export_state BLOB DEFAULT NULL")
    db.execSQL("ALTER TABLE sms ADD COLUMN exported INTEGER DEFAULT 0")
  }
}
