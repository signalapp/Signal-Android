/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Ensure archive_transfer_state is clear if an attachment is missing a remote_key.
 */
@Suppress("ClassName")
object V287_FixInvalidArchiveState : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE attachment SET archive_cdn = null, archive_transfer_state = 0 WHERE remote_key IS NULL AND archive_transfer_state = 3")
  }
}
