/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We want to switch [BackupMediaSnapshotTable] to use versions instead of timestamps.
 */
object V278_BackupSnapshotTableVersions : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE backup_media_snapshot")
    db.execSQL(
      """
      CREATE TABLE backup_media_snapshot (
        _id INTEGER PRIMARY KEY,
        media_id TEXT NOT NULL UNIQUE,
        cdn INTEGER,
        snapshot_version INTEGER NOT NULL DEFAULT -1,
        is_pending INTEGER NOT NULL DEFAULT 0,
        is_thumbnail INTEGER NOT NULL DEFAULT 0,
        remote_digest BLOB NOT NULL,
        last_seen_on_remote_snapshot_version INTEGER NOT NULL DEFAULT 0
      )
      """
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS backup_snapshot_version_index ON backup_media_snapshot (snapshot_version DESC) WHERE snapshot_version != -1")
  }
}
