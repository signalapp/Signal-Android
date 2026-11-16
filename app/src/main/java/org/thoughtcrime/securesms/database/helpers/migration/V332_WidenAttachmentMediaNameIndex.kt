/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Widens the (data_hash_end, remote_key) index with archive_transfer_state and message_id so archive reconciliation can scan it without touching the table.
 */
@Suppress("ClassName")
object V332_WidenAttachmentMediaNameIndex : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_media_name_index ON attachment (data_hash_end, remote_key, archive_transfer_state, message_id)")
    db.execSQL("DROP INDEX IF EXISTS attachment_data_hash_end_remote_key_index")
  }
}
