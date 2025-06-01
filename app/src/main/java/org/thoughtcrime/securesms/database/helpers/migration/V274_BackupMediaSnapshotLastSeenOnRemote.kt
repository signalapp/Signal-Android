/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Added a column to the backup media snapshot table to keep track of the last time we saw an object on the CDN.
 */
object V274_BackupMediaSnapshotLastSeenOnRemote : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE backup_media_snapshot ADD COLUMN last_seen_on_remote_timestamp INTEGER DEFAULT 0")
  }
}
