/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

object V231_ArchiveThumbnailColumns : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_thumbnail_cdn INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_thumbnail_media_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE attachment ADD COLUMN thumbnail_file TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE attachment ADD COLUMN thumbnail_random BLOB DEFAULT NULL")
  }
}
