/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * In order to make sure the snippet URI is not overwritten by the wrong message attachment, we want to
 * track the snippet message id in the thread table.
 */
@Suppress("ClassName")
object V282_AddSnippetMessageIdColumnToThreadTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE thread ADD COLUMN snippet_message_id INTEGER DEFAULT 0")
  }
}
