/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We need to keep track of the local backup key
 */
@Suppress("ClassName")
object V299_AddAttachmentMetadataTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE attachment_metadata (
        _id INTEGER PRIMARY KEY,
        plaintext_hash TEXT NOT NULL,
        local_backup_key BLOB DEFAULT NULL,
        UNIQUE (plaintext_hash)
      )
    """
    )

    db.execSQL("ALTER TABLE attachment ADD COLUMN metadata_id INTEGER DEFAULT NULL REFERENCES attachment_metadata (_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_metadata_id ON attachment (metadata_id)")
  }
}
