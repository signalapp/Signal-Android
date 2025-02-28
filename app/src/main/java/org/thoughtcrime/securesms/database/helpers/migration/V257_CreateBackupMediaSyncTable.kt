/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V257_CreateBackupMediaSyncTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE backup_media_snapshot (
          _id INTEGER PRIMARY KEY,
          media_id TEXT UNIQUE,
          cdn INTEGER,
          last_sync_time INTEGER DEFAULT 0,
          pending_sync_time INTEGER
      )
      """.trimIndent()
    )
  }
}
