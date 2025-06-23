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
 * Cleans up the call events table and restricts peer to non-null.
 */
@Suppress("ClassName")
object V196_BackCallLinksWithRecipientV2 : SignalDatabaseMigration {

  private val TAG = Log.tag(V196_BackCallLinksWithRecipientV2::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (!SqlUtil.columnExists(db, "recipient", "call_link_room_id")) {
      // add new column to recipient table to store the room id
      db.execSQL("ALTER TABLE recipient ADD COLUMN call_link_room_id TEXT DEFAULT NULL")

      // recreate the call link table with the new recipient id reference.
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
          avatar_color TEXT NOT NULL,
          recipient_id INTEGER UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE
        )
        """.trimIndent()
      )
    }

    // drop all call events with a null peer.
    db.execSQL("DELETE FROM call WHERE peer IS NULL")

    // drop all call events that have a later (by insertion) duplicate (by peer and call_id)
    db.execSQL(
      """
      DELETE FROM call
      WHERE _id < (
          SELECT MAX(_id) FROM call c2
          WHERE c2.peer = call.peer AND c2.call_id = call.call_id
      )
      """.trimIndent()
    )

    // recreate the call table dropping the call_link column
    db.execSQL(
      """
      CREATE TABLE call_tmp (
        _id INTEGER PRIMARY KEY,
        call_id INTEGER NOT NULL,
        message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE SET NULL,
        peer INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        type INTEGER NOT NULL,
        direction INTEGER NOT NULL,
        event INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        ringer INTEGER DEFAULT NULL,
        deletion_timestamp INTEGER DEFAULT 0,
        UNIQUE (call_id, peer) ON CONFLICT FAIL
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO call_tmp
      SELECT
        _id,
        call_id,
        message_id,
        peer,
        type,
        direction,
        event,
        timestamp,
        ringer,
        deletion_timestamp
      FROM call
      """.trimIndent()
    )

    db.execSQL("DROP TABLE call")
    db.execSQL("ALTER TABLE call_tmp RENAME TO call")

    db.execSQL("CREATE INDEX IF NOT EXISTS call_call_id_index ON call (call_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS call_message_id_index ON call (message_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS call_peer_index ON call (peer)")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "call")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
  }
}
