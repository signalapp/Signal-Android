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
 * The android app was the only one enforcing unique story names.
 * It's been decided dupes are ok, so we get to do the table recreation dance.
 */
object V212_RemoveDistributionListUniqueConstraint : SignalDatabaseMigration {

  private val TAG = Log.tag(V212_RemoveDistributionListUniqueConstraint::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    rebuildMainTable(db)
    stopwatch.split("main-table")

    rebuildMembershipTable(db)
    stopwatch.split("members-table")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "distribution_list") + SqlUtil.getForeignKeyViolations(db, "distribution_list_member")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }

  private fun rebuildMainTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE distribution_list_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        distribution_id TEXT UNIQUE NOT NULL,
        recipient_id INTEGER UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE,
        allows_replies INTEGER DEFAULT 1,
        deletion_timestamp INTEGER DEFAULT 0,
        is_unknown INTEGER DEFAULT 0,
        privacy_mode INTEGER DEFAULT 0
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO distribution_list_tmp
      SELECT
        _id,
        name,
        distribution_id,
        recipient_id,
        allows_replies,
        deletion_timestamp,
        is_unknown,
        privacy_mode
      FROM distribution_list
      """
    )

    db.execSQL("DROP TABLE distribution_list")
    db.execSQL("ALTER TABLE distribution_list_tmp RENAME TO distribution_list")
  }

  private fun rebuildMembershipTable(db: SQLiteDatabase) {
    db.execSQL("DROP INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index")
    db.execSQL("DROP INDEX distribution_list_member_recipient_id")

    db.execSQL(
      """
      CREATE TABLE distribution_list_member_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        list_id INTEGER NOT NULL REFERENCES distribution_list (_id) ON DELETE CASCADE,
        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        privacy_mode INTEGER DEFAULT 0
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO distribution_list_member_tmp
      SELECT
        _id,
        list_id,
        recipient_id,
        privacy_mode
      FROM distribution_list_member
      """
    )

    db.execSQL("DROP TABLE distribution_list_member")
    db.execSQL("ALTER TABLE distribution_list_member_tmp RENAME TO distribution_list_member")

    db.execSQL("CREATE UNIQUE INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index ON distribution_list_member (list_id, recipient_id, privacy_mode)")
    db.execSQL("CREATE INDEX distribution_list_member_recipient_id ON distribution_list_member (recipient_id)")
  }
}
