/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds archive_cdn and archive_media to attachment.
 */
@Suppress("ClassName")
object V224_AddAttachmentArchiveColumns : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_cdn INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_media_name TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_media_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_transfer_file TEXT DEFAULT NULL")
  }
}
