/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a pinned column to the message table to support pinning messages.
 */
@Suppress("ClassName")
object V292_AddPinnedMessageColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN pinned INTEGER DEFAULT 0;")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_pinned_index ON message (pinned);")
  }
}