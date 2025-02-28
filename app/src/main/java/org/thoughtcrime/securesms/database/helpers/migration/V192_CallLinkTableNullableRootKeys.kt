/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Allow ROOT_KEY in CallLinkTable to be null.
 */
@Suppress("ClassName")
object V192_CallLinkTableNullableRootKeys : SignalDatabaseMigration {

  private val TAG = Log.tag(V192_CallLinkTableNullableRootKeys::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE call_link")
    db.execSQL(
      """
      CREATE TABLE call_link (
        _id INTEGER PRIMARY KEY,
        root_key BLOB,
        room_id TEXT NOT NULL UNIQUE,
        admin_key BLOB,
        name TEXT NOT NULL,
        restrictions INTEGER NOT NULL,
        revoked INTEGER NOT NULL,
        expiration INTEGER NOT NULL,
        avatar_color TEXT NOT NULL
      )
      """.trimIndent()
    )

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "call")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
  }
}
