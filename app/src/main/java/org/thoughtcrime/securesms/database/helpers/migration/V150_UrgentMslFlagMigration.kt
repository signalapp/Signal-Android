package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adding an urgent flag to message envelopes to help with notifications. Need to track flag in
 * MSL table so can be resent with the correct urgency.
 */
@Suppress("ClassName")
object V150_UrgentMslFlagMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE msl_payload ADD COLUMN urgent INTEGER NOT NULL DEFAULT 1")
  }
}
