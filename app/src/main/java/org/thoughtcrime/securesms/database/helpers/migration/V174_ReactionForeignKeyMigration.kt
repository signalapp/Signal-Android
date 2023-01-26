package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adds a foreign key dependency between reactions and messages so we can remove the trigger.
 * Also renames mms -> message because I feel like I should have done that before.
 */
@Suppress("ClassName")
object V174_ReactionForeignKeyMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE reaction_tmp (
        _id INTEGER PRIMARY KEY,
        message_id INTEGER NOT NULL REFERENCES mms (_id) ON DELETE CASCADE,
        author_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        emoji TEXT NOT NULL,
        date_sent INTEGER NOT NULL,
        date_received INTEGER NOT NULL,
        UNIQUE(message_id, author_id) ON CONFLICT REPLACE
      );
      """
    )

    db.execSQL(
      """
        INSERT INTO reaction_tmp
        SELECT
          _id,
          message_id,
          author_id,
          emoji,
          date_sent,
          date_received
        FROM reaction
        WHERE message_id IN (SELECT _id FROM mms)
      """
    )

    db.execSQL("DROP TABLE reaction")
    db.execSQL("DROP TRIGGER IF EXISTS reactions_mms_delete")
    db.execSQL("ALTER TABLE reaction_tmp RENAME TO reaction")

    db.execSQL("ALTER TABLE mms RENAME TO message")
  }
}
