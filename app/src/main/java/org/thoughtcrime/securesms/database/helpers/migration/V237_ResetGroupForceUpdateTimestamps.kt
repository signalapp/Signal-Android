/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Reset last forced update timestamp for groups to fix a local group state bug.
 */
@Suppress("ClassName")
object V237_ResetGroupForceUpdateTimestamps : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE groups SET last_force_update_timestamp = 0")
  }
}
