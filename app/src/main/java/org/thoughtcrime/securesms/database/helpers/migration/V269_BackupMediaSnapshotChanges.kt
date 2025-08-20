/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We made a change to stop storing mediaId/names in favor of computing them on-the-fly.
 * So, this change removes those columns and adds some plumbing elsewhere that we need to keep things glued together correctly.
 */
object V269_BackupMediaSnapshotChanges : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX attachment_archive_media_id_index")
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_media_id")
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_media_name")
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_thumbnail_media_id")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_remote_digest_index ON attachment (remote_digest);")

    db.execSQL("ALTER TABLE backup_media_snapshot ADD COLUMN is_thumbnail INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE backup_media_snapshot ADD COLUMN remote_digest BLOB NOT NULL")
  }
}
