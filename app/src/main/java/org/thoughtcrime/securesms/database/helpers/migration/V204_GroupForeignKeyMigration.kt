/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Back CallLinks with a Recipient to ease integration and ensure we can support
 * different features which would require that relation in the future.
 */
@Suppress("ClassName")
object V204_GroupForeignKeyMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V204_GroupForeignKeyMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")
    db.execSQL(
      """
        CREATE TABLE groups_tmp (
          _id INTEGER PRIMARY KEY, 
          group_id TEXT NOT NULL UNIQUE, 
          recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE,
          title TEXT DEFAULT NULL,
          avatar_id INTEGER DEFAULT 0, 
          avatar_key BLOB DEFAULT NULL,
          avatar_content_type TEXT DEFAULT NULL, 
          avatar_digest BLOB DEFAULT NULL, 
          timestamp INTEGER DEFAULT 0,
          active INTEGER DEFAULT 1,
          mms INTEGER DEFAULT 0, 
          master_key BLOB DEFAULT NULL, 
          revision BLOB DEFAULT NULL, 
          decrypted_group BLOB DEFAULT NULL, 
          expected_v2_id TEXT UNIQUE DEFAULT NULL, 
          unmigrated_v1_members TEXT DEFAULT NULL, 
          distribution_id TEXT UNIQUE DEFAULT NULL, 
          show_as_story_state INTEGER DEFAULT 0, 
          last_force_update_timestamp INTEGER DEFAULT 0
        )
      """
    )

    val danglingRecipientCount = db.delete("groups", "recipient_id NOT IN (SELECT _id FROM recipient)", null)
    Log.i(TAG, "There were $danglingRecipientCount groups that referenced non-existent recipients.")

    db.execSQL(
      """
        INSERT INTO groups_tmp SELECT
          _id,
          group_id,
          recipient_id,
          title,
          avatar_id,
          avatar_key,
          avatar_content_Type,
          avatar_digest,
          timestamp,
          active,
          mms,
          master_key,
          revision,
          decrypted_group,
          expected_v2_id,
          former_v1_members,
          distribution_id,
          display_as_story,
          last_force_update_timestamp
        FROM groups
      """
    )
    stopwatch.split("groups-table")

    db.execSQL("DROP TABLE groups")
    db.execSQL("ALTER TABLE groups_tmp RENAME TO groups")

    db.execSQL(
      """
        CREATE TABLE group_membership_tmp (
          _id INTEGER PRIMARY KEY,
          group_id TEXT NOT NULL REFERENCES groups (group_id) ON DELETE CASCADE,
          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
          UNIQUE(group_id, recipient_id)
        )
      """
    )

    val danglingMemberCount = db.delete("group_membership", "group_id NOT IN (SELECT group_id FROM groups)", null)
    Log.i(TAG, "There were $danglingMemberCount members that referenced non-existent groups.")

    db.execSQL(
      """
        INSERT INTO group_membership_tmp SELECT * FROM group_membership
      """
    )
    stopwatch.split("membership-table")

    db.execSQL("DROP TABLE group_membership")
    db.execSQL("ALTER TABLE group_membership_tmp RENAME TO group_membership")

    db.execSQL("CREATE INDEX IF NOT EXISTS group_membership_recipient_id ON group_membership (recipient_id)")

    stopwatch.split("membership-index")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "groups") + SqlUtil.getForeignKeyViolations(db, "group_membership")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }
}
