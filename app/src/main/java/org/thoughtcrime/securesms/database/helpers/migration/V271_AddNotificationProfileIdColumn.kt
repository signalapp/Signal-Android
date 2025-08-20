/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase
import java.util.UUID

/**
 * Add notification_profile_id column to Notification Profiles to support backups.
 */
@Suppress("ClassName")
object V271_AddNotificationProfileIdColumn : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE notification_profile ADD COLUMN notification_profile_id TEXT DEFAULT NULL")

    db.rawQuery("SELECT _id FROM notification_profile")
      .readToList { it.requireLong("_id") }
      .forEach { id ->
        db.execSQL("UPDATE notification_profile SET notification_profile_id = '${UUID.randomUUID()}' WHERE _id = $id")
      }
  }
}
