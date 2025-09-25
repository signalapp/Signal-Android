/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Copy data_hash_start to data_hash_end for sticker attachments that have completed transfer.
 */
@Suppress("ClassName")
object V288_CopyStickerDataHashStartToEnd : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      "UPDATE attachment SET data_hash_end = data_hash_start WHERE sticker_pack_id IS NOT NULL AND data_hash_start IS NOT NULL AND data_hash_end IS NULL AND transfer_state = 0"
    )
  }
}
