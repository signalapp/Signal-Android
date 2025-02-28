/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a partial index for some performance-critical queries around unread counts.
 */
@Suppress("ClassName")
object V230_UnreadCountIndices : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_thread_unread_count_index ON message (thread_id) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND latest_revision_id IS NULL AND read = 0")
  }
}
