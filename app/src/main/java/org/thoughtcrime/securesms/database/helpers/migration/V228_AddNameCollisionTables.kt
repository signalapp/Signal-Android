/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the tables for managing name collisions
 */
object V228_AddNameCollisionTables : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE name_collision (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        thread_id INTEGER UNIQUE NOT NULL,
        dismissed INTEGER DEFAULT 0,
        hash STRING DEFAULT NULL
      )
    """
    )

    db.execSQL(
      """
      CREATE TABLE name_collision_membership (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        collision_id INTEGER NOT NULL REFERENCES name_collision (_id) ON DELETE CASCADE,
        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        profile_change_details BLOB DEFAULT NULL,
        UNIQUE (collision_id, recipient_id)
      )
    """
    )

    db.execSQL("CREATE INDEX name_collision_membership_collision_id_index ON name_collision_membership (collision_id)")
    db.execSQL("CREATE INDEX name_collision_membership_recipient_id_index ON name_collision_membership (recipient_id)")
  }
}
