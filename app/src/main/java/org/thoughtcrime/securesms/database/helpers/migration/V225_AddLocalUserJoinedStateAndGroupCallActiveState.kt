/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds local user joined state and group call active state to the
 * call events table for proper representation of missed non-ringing
 * group calls.
 *
 * Pre-migration call events will display as if the user joined the call.
 */
@Suppress("ClassName")
object V225_AddLocalUserJoinedStateAndGroupCallActiveState : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE call ADD COLUMN local_joined INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE call ADD COLUMN group_call_active INTEGER DEFAULT 0")

    /**
     * Assume for pre-migration calls that we've joined them all. This avoids
     * erroneously marking calls as missed.
     */
    db.execSQL("UPDATE call SET local_joined = 1")
  }
}
