package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adds a unique constraint to chat folder membership
 */
@Suppress("ClassName")
object V254_AddChatFolderConstraint : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX IF EXISTS chat_folder_membership_chat_folder_id_index")
    db.execSQL("DROP INDEX IF EXISTS chat_folder_membership_thread_id_index")
    db.execSQL("DROP INDEX IF EXISTS chat_folder_membership_membership_type_index")

    db.execSQL(
      """
      CREATE TABLE chat_folder_membership_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_folder_id INTEGER NOT NULL REFERENCES chat_folder (_id) ON DELETE CASCADE,
        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,
        membership_type INTEGER DEFAULT 1,
        UNIQUE(chat_folder_id, thread_id) ON CONFLICT REPLACE
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO chat_folder_membership_tmp
      SELECT
        _id,
        chat_folder_id,
        thread_id,
        membership_type
      FROM chat_folder_membership
      """
    )

    db.execSQL("DROP TABLE chat_folder_membership")
    db.execSQL("ALTER TABLE chat_folder_membership_tmp RENAME TO chat_folder_membership")

    db.execSQL("CREATE INDEX chat_folder_membership_thread_id_index ON chat_folder_membership (thread_id)")
    db.execSQL("CREATE INDEX chat_folder_membership_membership_type_index ON chat_folder_membership (membership_type)")
  }
}
