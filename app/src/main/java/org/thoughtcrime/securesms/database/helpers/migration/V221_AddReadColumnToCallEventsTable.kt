/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds read state to call events to separately track from the primary messages table.
 * Copies the current read state in from the message database, and then clears the message
 * database 'read' flag as well as decrements the unread count in the thread databse.
 */
@Suppress("ClassName")
object V221_AddReadColumnToCallEventsTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE call ADD COLUMN read INTEGER DEFAULT 1")

    db.execSQL(
      """
      UPDATE call
      SET read = (SELECT read FROM message WHERE _id = call.message_id)
      WHERE event = 3 AND direction = 0
      """.trimIndent()
    )

    db.execSQL(
      """
      UPDATE thread
      SET unread_count = thread.unread_count - 1
      WHERE _id IN (SELECT thread_id FROM message WHERE (type = 3 OR type = 8) AND read = 0) AND unread_count > 0
      """.trimIndent()
    )

    db.execSQL(
      """
      UPDATE message
      SET read = 1
      WHERE (type = 3 OR type = 8) AND read = 0
      """.trimIndent()
    )
  }
}
