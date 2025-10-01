/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the tables and indexes necessary for polls
 */
@Suppress("ClassName")
object V292_AddPollTables : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE poll (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        author_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        message_id INTEGER NOT NULL REFERENCES message (_id) ON DELETE CASCADE,
        question TEXT,
        allow_multiple_votes INTEGER DEFAULT 0,
        end_message_id INTEGER DEFAULT 0
      )
    """
    )

    db.execSQL(
      """
      CREATE TABLE poll_option (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        poll_id INTEGER NOT NULL REFERENCES poll (_id) ON DELETE CASCADE,
        option_text TEXT,
        option_order INTEGER
      )
      """
    )

    db.execSQL(
      """
      CREATE TABLE poll_vote (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        poll_id INTEGER NOT NULL REFERENCES poll (_id) ON DELETE CASCADE,
        poll_option_id INTEGER DEFAULT NULL REFERENCES poll_option (_id) ON DELETE CASCADE,
        voter_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        vote_count INTEGER,
        date_received INTEGER DEFAULT 0,
        vote_state INTEGER DEFAULT 0,
        UNIQUE(poll_id, voter_id, poll_option_id) ON CONFLICT REPLACE
      )
      """
    )

    db.execSQL("CREATE INDEX poll_author_id_index ON poll (author_id)")
    db.execSQL("CREATE INDEX poll_message_id_index ON poll (message_id)")

    db.execSQL("CREATE INDEX poll_option_poll_id_index ON poll_option (poll_id)")

    db.execSQL("CREATE INDEX poll_vote_poll_id_index ON poll_vote (poll_id)")
    db.execSQL("CREATE INDEX poll_vote_poll_option_id_index ON poll_vote (poll_option_id)")
    db.execSQL("CREATE INDEX poll_vote_voter_id_index ON poll_vote (voter_id)")

    db.execSQL("ALTER TABLE message ADD COLUMN votes_unread INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE message ADD COLUMN votes_last_seen INTEGER DEFAULT 0")
    db.execSQL("CREATE INDEX message_votes_unread_index ON message (votes_unread)")
  }
}
