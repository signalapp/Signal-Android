/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the ACTIVE column to the THREAD table, and mark all current threads active.
 *
 * For performance and maintainability reasons, instead of explicitly deleting thread rows from the
 * database, we instead want to mark them as inactive and remove any backing data (messages, etc.),
 * essentially turning them into tombstones, which can be 'resurrected' when a new attempt is made
 * to chat with whomever the thread is tied to.
 */
@Suppress("ClassName")
object V199_AddThreadActiveColumn : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE thread ADD COLUMN active INTEGER DEFAULT 0")
    db.execSQL("UPDATE thread SET active = 1")

    db.execSQL("DROP INDEX IF EXISTS thread_recipient_id_index")
    db.execSQL("DROP INDEX IF EXISTS archived_count_index")

    db.execSQL("CREATE INDEX IF NOT EXISTS thread_recipient_id_index ON thread (recipient_id, active)")
    db.execSQL("CREATE INDEX IF NOT EXISTS archived_count_index ON thread (active, archived, meaningful_messages, pinned)")
    db.execSQL("CREATE INDEX IF NOT EXISTS thread_active ON thread (active)")
  }
}
