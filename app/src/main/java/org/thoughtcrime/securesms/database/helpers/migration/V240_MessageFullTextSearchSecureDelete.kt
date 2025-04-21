/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Sets the 'secure-delete' flag on the message_fts table.
 * https://www.sqlite.org/fts5.html#the_secure_delete_configuration_option
 */
@Suppress("ClassName")
object V240_MessageFullTextSearchSecureDelete : SignalDatabaseMigration {

  const val FTS_TABLE_NAME = "message_fts"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("""INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) VALUES('secure-delete', 1);""")
  }
}
