/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We were unnecessarily holding on to some attachment download data for viewed view-once messages that we don't need to hold onto.
 */
object V283_ViewOnceRemoteDataCleanup : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE 
        attachment
      SET 
        remote_key = NULL,
        remote_digest = NULL,
        remote_incremental_digest = NULL,
        remote_incremental_digest_chunk_size = 0,
        thumbnail_file = NULL,
        thumbnail_random = NULL,
        archive_transfer_state = 0
      WHERE 
        data_file IS NULL AND
        content_type = 'application/x-signal-view-once'
      """
    )
  }
}
