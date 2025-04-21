/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Recreates the message FTS stuff, but with a tokenizer property that lets us search for emoji.
 * This is paired with an ApplicationMigration to rebuild the message index in the background.
 *
 * This is the second attempt (see [V239_MessageFullTextSearchEmojiSupport]). The first attempt saw weird vtable issues.
 */
@Suppress("ClassName")
object V242_MessageFullTextSearchEmojiSupportV2 : SignalDatabaseMigration {

  private const val FTS_TABLE_NAME = "message_fts"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Due to issues we've had in the past, the delete sequence here is very particular. It mimics the "safe drop" process in the SQLite source code
    // that prevents weird vtable constructor issues when dropping potentially-corrupt tables. https://sqlite.org/src/info/4db9258a78?ln=1549-1592
    db.execSQL("DELETE FROM ${FTS_TABLE_NAME}_data")
    db.execSQL("DELETE FROM ${FTS_TABLE_NAME}_config")
    db.execSQL("INSERT INTO ${FTS_TABLE_NAME}_data VALUES(10, X'0000000000')")
    db.execSQL("INSERT INTO ${FTS_TABLE_NAME}_config VALUES('version', 4)")
    db.execSQL("DROP TABLE $FTS_TABLE_NAME")

    db.execSQL("DROP TRIGGER IF EXISTS message_ai")
    db.execSQL("DROP TRIGGER IF EXISTS message_ad")
    db.execSQL("DROP TRIGGER IF EXISTS message_au")

    db.execSQL("""CREATE VIRTUAL TABLE message_fts USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id, tokenize = "unicode61 categories 'L* N* Co Sc So'")""")
    db.execSQL("INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) VALUES('secure-delete', 1)")
    db.execSQL("INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME) VALUES('rebuild')")

    db.execSQL(
      """
      CREATE TRIGGER message_ai AFTER INSERT ON message BEGIN
        INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);
      END;
      """
    )
    db.execSQL(
      """
      CREATE TRIGGER message_ad AFTER DELETE ON message BEGIN
        INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);
      END;
    """
    )
    db.execSQL(
      """
      CREATE TRIGGER message_au AFTER UPDATE ON message BEGIN
        INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id);
        INSERT INTO message_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);
      END;
      """
    )
  }
}
