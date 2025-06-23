/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Keep track of a "stale timestamp" for one-time prekeys so that we can know when it's safe to delete them.
 */
@Suppress("ClassName")
object V203_PreKeyStaleTimestamp : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Note: Because of a sequencing issue between beta/nightly, we had two V202 migrations (of which this used to be one of them),
    //       so we have to do some conditional migrating based on the user's current state.
    db.execSQL("DROP INDEX IF EXISTS message_thread_date_index")

    if (!columnExists(db, "one_time_prekeys", "stale_timestamp")) {
      db.execSQL("ALTER TABLE one_time_prekeys ADD COLUMN stale_timestamp INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE kyber_prekey ADD COLUMN stale_timestamp INTEGER NOT NULL DEFAULT 0")
    }
  }

  private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info($table)", arrayOf()).use { cursor ->
      val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
      while (cursor.moveToNext()) {
        val name = cursor.getString(nameColumnIndex)
        if (name == column) {
          return true
        }
      }
    }
    return false
  }
}
