/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the offload_restored_at column to attachments.
 *
 * Important: May be ran twice depending on people's upgrade path during the beta.
 */
@Suppress("ClassName")
object V252_AttachmentOffloadRestoredAtColumn : SignalDatabaseMigration {

  private val TAG = Log.tag(V252_AttachmentOffloadRestoredAtColumn::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqlUtil.columnExists(db, "attachment", "offload_restored_at")) {
      Log.i(TAG, "Already ran migration!")
      return
    }

    db.execSQL("ALTER TABLE attachment ADD COLUMN offload_restored_at INTEGER DEFAULT 0;")
  }
}
