/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Recreates the message FTS stuff, but with a tokenizer property that lets us search for emoji.
 * This is paired with an ApplicationMigration to rebuild the message index in the background.
 */
@Suppress("ClassName")
object V239_MessageFullTextSearchEmojiSupport : SignalDatabaseMigration {

  private val TAG = Log.tag(V239_MessageFullTextSearchEmojiSupport::class.java)

  private const val FTS_TABLE_NAME = "message_fts"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    try {
      doMigration(db)
    } catch (t: Throwable) {
      // For some unknown reason, a select few users are hitting a 'vtable constructor' crash when trying to drop the FTS table.
      // This feature is not critical, so for now we'll take the loss in those cases and try to synchronize it later.
      // The migration doesn't change the actual schema, just the tokenization, so it shouldn't be that big of a deal.
      Log.e(TAG, "Failed to perform migration!", t)
    }
  }

  private fun doMigration(db: SQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE_NAME")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_config")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_content")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_data")
    db.execSQL("DROP TABLE IF EXISTS ${FTS_TABLE_NAME}_idx")
    db.execSQL("DROP TRIGGER IF EXISTS message_ai")
    db.execSQL("DROP TRIGGER IF EXISTS message_ad")
    db.execSQL("DROP TRIGGER IF EXISTS message_au")

    db.execSQL("""CREATE VIRTUAL TABLE message_fts USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id, tokenize = "unicode61 categories 'L* N* Co Sc So'")""")

    db.execSQL("INSERT INTO message_fts(message_fts) VALUES ('rebuild')")

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
