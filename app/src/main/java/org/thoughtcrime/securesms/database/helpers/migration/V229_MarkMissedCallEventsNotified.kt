/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * In order to both correct how we display missed calls and not spam users,
 * we want to mark every missed call event in the database as notified.
 */
@Suppress("ClassName")
object V229_MarkMissedCallEventsNotified : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE message
      SET notified = 1
      WHERE (type = 3) OR (type = 8)
      """.trimIndent()
    )
  }
}
