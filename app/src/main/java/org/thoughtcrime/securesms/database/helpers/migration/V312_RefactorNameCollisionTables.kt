/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Refactors name collision tables so that a single collision record can be shared across
 * multiple threads. Previously, each thread had its own collision row with duplicated members,
 * leading to O(N^2) membership rows when N recipients shared a name. Now a collision just stores
 * a hash, and a new name_collision_thread table links threads to collisions with per-thread
 * dismissed state.
 */
object V312_RefactorNameCollisionTables : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Step 1: Create the new thread linking table
    db.execSQL(
      """
      CREATE TABLE name_collision_thread (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        collision_id INTEGER NOT NULL REFERENCES name_collision (_id) ON DELETE CASCADE,
        thread_id INTEGER UNIQUE NOT NULL,
        dismissed INTEGER DEFAULT 0
      )
      """
    )
    db.execSQL("CREATE INDEX name_collision_thread_collision_id_index ON name_collision_thread (collision_id)")

    // Step 2: Populate thread links from existing collision data
    db.execSQL(
      """
      INSERT INTO name_collision_thread (collision_id, thread_id, dismissed)
      SELECT _id, thread_id, dismissed
      FROM name_collision
      """
    )

    // Step 3: Recreate name_collision without thread_id and dismissed columns
    db.execSQL(
      """
      CREATE TABLE name_collision_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        hash STRING DEFAULT NULL
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO name_collision_tmp (_id, hash)
      SELECT _id, hash
      FROM name_collision
      """
    )

    db.execSQL("DROP TABLE name_collision")
    db.execSQL("ALTER TABLE name_collision_tmp RENAME TO name_collision")
  }
}
