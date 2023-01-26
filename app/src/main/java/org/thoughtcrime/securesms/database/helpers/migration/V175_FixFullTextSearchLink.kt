package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Turns out renaming a table will automatically update all of your indexes, foreign keys, triggers, basically everything... except full-text search tables.
 * So we have to delete it and rebuild it.
 */
@Suppress("ClassName")
object V175_FixFullTextSearchLink : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE mms_fts")
    db.execSQL("DROP TRIGGER IF EXISTS mms_ai")
    db.execSQL("DROP TRIGGER IF EXISTS mms_ad")
    db.execSQL("DROP TRIGGER IF EXISTS mms_au")

    db.execSQL("CREATE VIRTUAL TABLE message_fts USING fts5(body, thread_id UNINDEXED, content=message, content_rowid=_id)")

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
        INSERT INTO message_fts(message_fts, rowid, body, thread_id) VALUES ('delete', old._id, old.body, old.thread_id);
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
