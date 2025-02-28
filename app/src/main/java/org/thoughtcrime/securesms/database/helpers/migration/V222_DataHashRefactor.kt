/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the new data hash columns and indexes.
 */
@Suppress("ClassName")
object V222_DataHashRefactor : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX attachment_data_hash_index")
    db.execSQL("ALTER TABLE attachment DROP COLUMN data_hash")

    db.execSQL("ALTER TABLE attachment ADD COLUMN data_hash_start TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE attachment ADD COLUMN data_hash_end TEXT DEFAULT NULL")
    db.execSQL("CREATE INDEX attachment_data_hash_start_index ON attachment (data_hash_start)")
    db.execSQL("CREATE INDEX attachment_data_hash_end_index ON attachment (data_hash_end)")
  }
}
