/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Recreates the message FTS stuff, but with a tokenizer property that lets us search for emoji.
 * This is paired with an ApplicationMigration to rebuild the message index in the background.
 */
@Suppress("ClassName")
object V239_MessageFullTextSearchEmojiSupport : SignalDatabaseMigration {

  const val FTS_TABLE_NAME = "message_fts"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_config")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_content")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_data")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_idx")
    db.execSQL("DROP TRIGGER IF EXISTS message_ai")
    db.execSQL("DROP TRIGGER IF EXISTS message_ad")
    db.execSQL("DROP TRIGGER IF EXISTS message_au")

    db.execSQL("""CREATE VIRTUAL TABLE message_fts USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id, tokenize = "unicode61 categories 'L* N* Co Sc So'")""")

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
