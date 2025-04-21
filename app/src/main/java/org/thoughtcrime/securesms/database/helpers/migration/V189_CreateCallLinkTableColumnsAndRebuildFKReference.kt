/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Fleshes out the call link table and rebuilds the call event table.
 * At this point, there should be no records in the call link database.
 */
@Suppress("ClassName")
object V189_CreateCallLinkTableColumnsAndRebuildFKReference : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE call_link_tmp (
        _id INTEGER PRIMARY KEY,
        root_key BLOB NOT NULL,
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

    db.execSQL(
      """
      CREATE TABLE call_tmp (
        _id INTEGER PRIMARY KEY,
        call_id INTEGER NOT NULL,
        message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE SET NULL,
        peer INTEGER DEFAULT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        call_link TEXT DEFAULT NULL REFERENCES call_link (room_id) ON DELETE CASCADE,
        type INTEGER NOT NULL,
        direction INTEGER NOT NULL,
        event INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        ringer INTEGER DEFAULT NULL,
        deletion_timestamp INTEGER DEFAULT 0,
        UNIQUE (call_id, peer, call_link) ON CONFLICT FAIL,
        CHECK ((peer IS NULL AND call_link IS NOT NULL) OR (peer IS NOT NULL AND call_link IS NULL))
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
        NULL as call_link,
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
    db.execSQL("DROP TABLE call_link")
    db.execSQL("ALTER TABLE call_link_tmp RENAME TO call_link")

    db.execSQL("CREATE INDEX IF NOT EXISTS call_call_id_index ON call (call_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS call_message_id_index ON call (message_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS call_call_link_index ON call (call_link)")
    db.execSQL("CREATE INDEX IF NOT EXISTS call_peer_index ON call (peer)")
  }
}
