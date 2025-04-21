/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * A while back we added an accountId to the prekey tables to support a mix of ACI and PNI identities.
 * Unfortunately, we didn't remove the unique constraint on the keyId, which isn't correct: the uniqueness
 * is now based on the combination of (accountId, keyId). This migration fixes that by removing the unique
 * address from the keyId column itself.
 */
object V220_PreKeyConstraints : SignalDatabaseMigration {

  private val TAG = Log.tag(V220_PreKeyConstraints::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration", decimalPlaces = 2)
    migrateSignedEcKeyTable(db)
    stopwatch.split("signed-ec")

    migrateOneTimeEcPreKeysTable(db)
    stopwatch.split("one-time-ec")

    migrateKyberTable(db)
    stopwatch.split("kyber")

    stopwatch.stop(TAG)
  }

  private fun migrateKyberTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE kyber_prekey_temp (
        _id INTEGER PRIMARY KEY,
        account_id TEXT NOT NULL,
        key_id INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        last_resort INTEGER NOT NULL,
        serialized BLOB NOT NULL,
        stale_timestamp INTEGER NOT NULL DEFAULT 0,
        UNIQUE(account_id, key_id)
      )
      """
    )

    db.execSQL("INSERT INTO kyber_prekey_temp SELECT * FROM kyber_prekey")

    db.execSQL("DROP TABLE kyber_prekey")
    db.execSQL("DROP INDEX IF EXISTS kyber_account_id_key_id")

    db.execSQL("ALTER TABLE kyber_prekey_temp RENAME TO kyber_prekey")
    db.execSQL("CREATE INDEX IF NOT EXISTS kyber_account_id_key_id ON kyber_prekey (account_id, key_id, last_resort, serialized)")
  }

  private fun migrateSignedEcKeyTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE signed_prekeys_temp (
        _id INTEGER PRIMARY KEY,
        account_id TEXT NOT NULL,
        key_id INTEGER NOT NULL,
        public_key TEXT NOT NULL,
        private_key TEXT NOT NULL,
        signature TEXT NOT NULL,
        timestamp INTEGER DEFAULT 0,
        UNIQUE(account_id, key_id)
      )
      """
    )

    db.execSQL("INSERT INTO signed_prekeys_temp SELECT * FROM signed_prekeys")

    db.execSQL("DROP TABLE signed_prekeys")

    db.execSQL("ALTER TABLE signed_prekeys_temp RENAME TO signed_prekeys")
  }

  private fun migrateOneTimeEcPreKeysTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE one_time_prekeys_temp (
        _id INTEGER PRIMARY KEY,
        account_id TEXT NOT NULL,
        key_id INTEGER NOT NULL,
        public_key TEXT NOT NULL,
        private_key TEXT NOT NULL,
        stale_timestamp INTEGER NOT NULL DEFAULT 0,
        UNIQUE(account_id, key_id)
      )
      """
    )

    db.execSQL("INSERT INTO one_time_prekeys_temp SELECT * FROM one_time_prekeys")

    db.execSQL("DROP TABLE one_time_prekeys")

    db.execSQL("ALTER TABLE one_time_prekeys_temp RENAME TO one_time_prekeys")
  }
}
