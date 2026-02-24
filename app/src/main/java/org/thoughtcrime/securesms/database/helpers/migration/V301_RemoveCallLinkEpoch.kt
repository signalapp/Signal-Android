/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We planned to introduce CallLink epochs as a first-class field to clients.
 * Now, we plan to introduce epochs as an internal detail in CallLink root keys.
 * Epochs were never enabled in production so no clients should have them.
 */
object V301_RemoveCallLinkEpoch : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE call_link DROP COLUMN epoch")
  }
}
