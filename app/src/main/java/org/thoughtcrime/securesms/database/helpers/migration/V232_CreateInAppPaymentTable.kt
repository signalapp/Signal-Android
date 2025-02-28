/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Create the table and migrate necessary data.
 */
@Suppress("ClassName")
object V232_CreateInAppPaymentTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE in_app_payment (
        _id INTEGER PRIMARY KEY,
        type INTEGER NOT NULL,
        state INTEGER NOT NULL,
        inserted_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        notified INTEGER DEFAULT 0,
        subscriber_id TEXT,
        end_of_period INTEGER DEFAULT 0,
        data BLOB NOT NULL
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE in_app_payment_subscriber (
        _id INTEGER PRIMARY KEY,
        subscriber_id TEXT NOT NULL UNIQUE,
        currency_code TEXT NOT NULL,
        type INTEGER NOT NULL,
        requires_cancel INTEGER DEFAULT 0,
        payment_method_type INTEGER DEFAULT 0,
        UNIQUE(currency_code, type)
      )
      """.trimIndent()
    )
  }
}
