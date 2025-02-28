/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Introduces [org.thoughtcrime.securesms.database.KyberPreKeyTable].
 */
@Suppress("ClassName")
object V194_KyberPreKeyMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE kyber_prekey (
        _id INTEGER PRIMARY KEY,
        account_id TEXT NOT NULL,
        key_id INTEGER UNIQUE NOT NULL, 
        timestamp INTEGER NOT NULL,
        last_resort INTEGER NOT NULL,
        serialized BLOB NOT NULL,
        UNIQUE(account_id, key_id)
      )
      """
    )

    db.execSQL("CREATE INDEX IF NOT EXISTS kyber_account_id_key_id ON kyber_prekey (account_id, key_id, last_resort, serialized)")
  }
}
