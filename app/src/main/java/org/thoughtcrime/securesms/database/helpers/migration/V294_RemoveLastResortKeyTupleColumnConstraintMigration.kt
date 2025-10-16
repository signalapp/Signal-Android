/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Removes the UNIQUE constraint from kyber_prekey_id field
 */
@Suppress("ClassName")
object V294_RemoveLastResortKeyTupleColumnConstraintMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE last_resort_key_tuple")

    db.execSQL(
      """
      CREATE TABLE last_resort_key_tuple (
        _id INTEGER PRIMARY KEY,
        kyber_prekey_id INTEGER NOT NULL REFERENCES kyber_prekey (_id) ON DELETE CASCADE,
        signed_key_id INTEGER NOT NULL,
        public_key BLOB NOT NULL,
        UNIQUE(kyber_prekey_id, signed_key_id, public_key)
      )
      """
    )
  }
}
