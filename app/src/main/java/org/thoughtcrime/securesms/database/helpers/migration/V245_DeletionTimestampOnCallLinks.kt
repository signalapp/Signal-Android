/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adds a deletion timestamp to the call links table, which is required for storage service syncing.
 */
object V245_DeletionTimestampOnCallLinks : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE call_link ADD COLUMN deletion_timestamp INTEGER DEFAULT 0 NOT NULL;")
  }
}
