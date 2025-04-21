/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Back CallLinks with a Recipient to ease integration and ensure we can support
 * different features which would require that relation in the future.
 */
@Suppress("ClassName")
object V195_GroupMemberForeignKeyMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V195_GroupMemberForeignKeyMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")
    db.execSQL(
      """
        CREATE TABLE group_membership_tmp (
          _id INTEGER PRIMARY KEY,
          group_id TEXT NOT NULL,
          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
          UNIQUE(group_id, recipient_id)
        )
      """
    )
    stopwatch.split("create")

    db.rawQuery("SELECT * FROM remapped_recipients")
      .readToList { it.requireLong("old_id") to it.requireLong("new_id") }
      .forEach { remapMembership(db, it) }
    stopwatch.split("fix-remapping")

    db.execSQL("DELETE FROM group_membership WHERE recipient_id NOT IN (SELECT _id FROM RECIPIENT)")
    stopwatch.split("trim-bad-fk")

    db.execSQL("INSERT INTO group_membership_tmp SELECT * FROM group_membership")
    db.execSQL("DROP TABLE group_membership")
    db.execSQL("ALTER TABLE group_membership_tmp RENAME TO group_membership")
    stopwatch.split("copy-data")

    db.execSQL("CREATE INDEX IF NOT EXISTS group_membership_recipient_id ON group_membership (recipient_id)")
    stopwatch.split("index")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "groups") + SqlUtil.getForeignKeyViolations(db, "group_membership")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }

  private fun remapMembership(db: SQLiteDatabase, remap: Pair<Long, Long>) {
    val fromId = remap.first
    val toId = remap.second

    db.execSQL(
      """
        UPDATE group_membership AS parent
        SET recipient_id = ?
        WHERE
          recipient_id = ?
          AND NOT EXISTS (
            SELECT 1
            FROM group_membership child
            WHERE 
              child.recipient_id = ?
              AND parent.group_id = child.group_id
          )
      """,
      SqlUtil.buildArgs(toId, fromId, toId)
    )
  }
}
