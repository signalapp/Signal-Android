/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds IAP fields and updates constraints.
 */
@Suppress("ClassName")
object V263_InAppPaymentsSubscriberTableRebuild : SignalDatabaseMigration {

  private const val DONOR_TYPE = 0
  private const val BACKUP_TYPE = 1

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE in_app_payment_subscriber_tmp (
        _id INTEGER PRIMARY KEY,
        subscriber_id TEXT NOT NULL UNIQUE,
        currency_code TEXT NOT NULL,
        type INTEGER NOT NULL,
        requires_cancel INTEGER DEFAULT 0,
        payment_method_type INTEGER DEFAULT 0,
        purchase_token TEXT,
        original_transaction_id INTEGER,
        UNIQUE(currency_code, type),
        CHECK ( 
          (currency_code != '' AND purchase_token IS NULL AND original_transaction_id IS NULL AND type = $DONOR_TYPE)
          OR (currency_code = '' AND purchase_token IS NOT NULL AND original_transaction_id IS NULL AND type = $BACKUP_TYPE)
          OR (currency_code = '' AND purchase_token IS NULL AND original_transaction_id IS NOT NULL AND type = $BACKUP_TYPE)
        )
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO in_app_payment_subscriber_tmp (_id, subscriber_id, currency_code, type, requires_cancel, payment_method_type, purchase_token)
      SELECT 
          _id, 
          subscriber_id, 
          CASE
              WHEN type = $DONOR_TYPE THEN currency_code
              ELSE ''
          END,
          type, 
          requires_cancel, 
          payment_method_type,
          CASE
              WHEN type = $BACKUP_TYPE THEN "-"
              ELSE NULL
          END
      FROM in_app_payment_subscriber
      """.trimIndent()
    )

    db.execSQL("DROP TABLE in_app_payment_subscriber")

    db.execSQL("ALTER TABLE in_app_payment_subscriber_tmp RENAME TO in_app_payment_subscriber")
  }
}
