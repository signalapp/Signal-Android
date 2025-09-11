/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * If remote_key is an empty byte array (base64 encoded), replace with null.
 */
@Suppress("ClassName")
object V291_NullOutRemoteKeyIfEmpty : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE attachment SET remote_key = NULL WHERE remote_key IS NOT NULL AND LENGTH(remote_key) = 0")
  }
}
