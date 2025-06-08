/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Base64
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Adds columns to notification profiles to support storage service, drops names unique constraint, sets all profiles with a storage service id.
 */
object V277_AddNotificationProfileStorageSync : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Rebuild table to drop unique constraint on 'name'
    db.execSQL(
      """
      CREATE TABLE notification_profile_temp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        emoji TEXT NOT NULL,
        color TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        allow_all_calls INTEGER NOT NULL DEFAULT 0,
        allow_all_mentions INTEGER NOT NULL DEFAULT 0,
        notification_profile_id TEXT DEFAULT NULL,
        deleted_timestamp_ms INTEGER DEFAULT 0,
        storage_service_id TEXT DEFAULT NULL,
        storage_service_proto TEXT DEFAULT NULL
      )
      """.trimIndent()
    )

    db.execSQL("INSERT INTO notification_profile_temp (_id, name, emoji, color, created_at, allow_all_calls, allow_all_mentions, notification_profile_id) SELECT _id, name, emoji, color, created_at, allow_all_calls, allow_all_mentions, notification_profile_id FROM notification_profile")
    db.execSQL("DROP TABLE notification_profile")
    db.execSQL("ALTER TABLE notification_profile_temp RENAME TO notification_profile")

    // Initialize all profiles with a storage service id
    db.rawQuery("SELECT _id FROM notification_profile")
      .readToList { it.requireLong("_id") }
      .forEach { id ->
        db.execSQL("UPDATE notification_profile SET storage_service_id = '${Base64.encodeWithPadding(StorageSyncHelper.generateKey())}' WHERE _id = $id")
      }
  }
}
