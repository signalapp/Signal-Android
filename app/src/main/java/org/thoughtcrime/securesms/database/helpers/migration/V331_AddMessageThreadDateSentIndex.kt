/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Covering index that lets us find every day a thread has messages on in a single pass.
 */
@Suppress("ClassName")
object V331_AddMessageThreadDateSentIndex : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_thread_date_sent_index ON message (thread_id, date_sent) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND latest_revision_id IS NULL")
  }
}
