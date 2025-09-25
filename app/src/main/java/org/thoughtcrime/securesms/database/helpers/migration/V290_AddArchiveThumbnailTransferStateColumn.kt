/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We need to keep track of a transfer state for thumbnails too.
 */
@Suppress("ClassName")
object V290_AddArchiveThumbnailTransferStateColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_thumbnail_transfer_state INTEGER DEFAULT 0;")
  }
}
