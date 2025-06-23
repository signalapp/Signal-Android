/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a message_extras column to the messages table. This allows us to
 * store extra data for messages in a more future proof and structured way.
 */
@Suppress("ClassName")
object V217_MessageTableExtrasColumn : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN message_extras BLOB DEFAULT NULL")
    db.execSQL("ALTER TABLE thread ADD COLUMN snippet_message_extras BLOB DEFAULT NULL")
  }
}
