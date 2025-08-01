/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We've changed our mediaName calculation to be based on plaintextHash + remoteKey instead of remoteDigest. That means we no longer need to store the IV
 * in the database, because the only reason we were storing it before was to have a consistent remoteDigest calculation.
 *
 * Also, because we're changing the mediaName calculation, we need to reset all of the archive status's.
 */
object V280_RemoveAttachmentIv : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment DROP COLUMN remote_iv")
    db.execSQL("DROP INDEX attachment_data_hash_end_index")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_hash_end_remote_key_index ON attachment (data_hash_end, remote_key)")

    // Rebuild table to allow us to have new non-null columns
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
        plaintext_hash BLOB NOT NULL,
        remote_key BLOB NOT NULL,
        last_seen_on_remote_snapshot_version INTEGER NOT NULL DEFAULT 0
      )
      """
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS backup_snapshot_version_index ON backup_media_snapshot (snapshot_version DESC) WHERE snapshot_version != -1")

    // Reset archive transfer state
    db.execSQL("UPDATE attachment SET archive_transfer_state = 0 WHERE archive_transfer_state != 0")
  }
}
