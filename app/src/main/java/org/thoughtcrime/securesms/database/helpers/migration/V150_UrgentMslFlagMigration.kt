package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adding an urgent flag to message envelopes to help with notifications. Need to track flag in
 * MSL table so can be resent with the correct urgency.
 */
object V150_UrgentMslFlagMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE msl_payload ADD COLUMN urgent INTEGER NOT NULL DEFAULT 1")
  }
}
