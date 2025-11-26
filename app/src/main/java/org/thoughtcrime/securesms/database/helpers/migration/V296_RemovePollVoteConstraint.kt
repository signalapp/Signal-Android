/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Removes the UNIQUE constraint from the poll vote table
 */
@Suppress("ClassName")
object V296_RemovePollVoteConstraint : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX IF EXISTS poll_vote_poll_id_index")
    db.execSQL("DROP INDEX IF EXISTS poll_vote_poll_option_id_index")
    db.execSQL("DROP INDEX IF EXISTS poll_vote_voter_id_index")

    db.execSQL(
      """
      CREATE TABLE poll_vote_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        poll_id INTEGER NOT NULL REFERENCES poll (_id) ON DELETE CASCADE,
        poll_option_id INTEGER DEFAULT NULL REFERENCES poll_option (_id) ON DELETE CASCADE,
        voter_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        vote_count INTEGER,
        date_received INTEGER DEFAULT 0,
        vote_state INTEGER DEFAULT 0
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO poll_vote_tmp
      SELECT
        _id,
        poll_id,
        poll_option_id,
        voter_id,
        vote_count,
        date_received,
        vote_state
      FROM poll_vote
      """
    )

    db.execSQL("DROP TABLE poll_vote")
    db.execSQL("ALTER TABLE poll_vote_tmp RENAME TO poll_vote")

    db.execSQL("CREATE INDEX poll_vote_poll_id_index ON poll_vote (poll_id)")
    db.execSQL("CREATE INDEX poll_vote_poll_option_id_index ON poll_vote (poll_option_id)")
    db.execSQL("CREATE INDEX poll_vote_voter_id_index ON poll_vote (voter_id)")
  }
}
