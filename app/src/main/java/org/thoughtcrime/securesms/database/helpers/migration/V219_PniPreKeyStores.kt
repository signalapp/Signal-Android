/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Changes our PNI prekey stores to use a constant indicating it's for a PNI rather than the specific PNI.
 */
@Suppress("ClassName")
object V219_PniPreKeyStores : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE one_time_prekeys
      SET account_id = "PNI"
      WHERE account_id LIKE "PNI:%"
      """
    )

    db.execSQL(
      """
      UPDATE signed_prekeys
      SET account_id = "PNI"
      WHERE account_id LIKE "PNI:%"
      """
    )

    db.execSQL(
      """
      UPDATE kyber_prekey 
      SET account_id = "PNI"
      WHERE account_id LIKE "PNI:%"
      """
    )
  }
}
