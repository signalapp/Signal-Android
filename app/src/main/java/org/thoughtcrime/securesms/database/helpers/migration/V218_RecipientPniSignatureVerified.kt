/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a pni_signature_verified column to the recipient table, letting us track whether the ACI/PNI association is verified and sync that to storage service.
 */
@Suppress("ClassName")
object V218_RecipientPniSignatureVerified : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN pni_signature_verified INTEGER DEFAULT 0")
  }
}
