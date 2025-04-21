/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 */
@Suppress("ClassName")
object V211_ReceiptColumnRenames : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message RENAME COLUMN delivery_receipt_count TO has_delivery_receipt")
    db.execSQL("ALTER TABLE message RENAME COLUMN read_receipt_count TO has_read_receipt")
    db.execSQL("ALTER TABLE message RENAME COLUMN viewed_receipt_count TO viewed")

    db.execSQL("ALTER TABLE thread RENAME COLUMN delivery_receipt_count TO has_delivery_receipt")
    db.execSQL("ALTER TABLE thread RENAME COLUMN read_receipt_count TO has_read_receipt")
  }
}
