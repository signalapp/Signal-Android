/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We used to decrypt archive files in two distinct steps, and therefore needed a secondary transfer file.
 * Now, we're able to do all of the decrypt in one stream, so we no longer need the intermediary transfer file.
 */
object V281_RemoveArchiveTransferFile : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_transfer_file")
  }
}
