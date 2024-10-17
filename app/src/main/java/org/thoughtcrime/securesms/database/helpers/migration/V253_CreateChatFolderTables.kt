package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.thoughtcrime.securesms.database.ChatFolderTables

/**
 * Adds the tables for managing chat folders
 */
@Suppress("ClassName")
object V253_CreateChatFolderTables : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE chat_folder (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT DEFAULT NULL,
        position INTEGER DEFAULT 0,
        show_unread INTEGER DEFAULT 0,
        show_muted INTEGER DEFAULT 0,
        show_individual INTEGER DEFAULT 0,
        show_groups INTEGER DEFAULT 0,
        is_muted INTEGER DEFAULT 0,
        folder_type INTEGER DEFAULT 4
      )
    """
    )

    db.execSQL(
      """
      CREATE TABLE chat_folder_membership (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_folder_id INTEGER NOT NULL REFERENCES chat_folder (_id) ON DELETE CASCADE,
        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,
        membership_type INTEGER DEFAULT 1
      )
      """
    )

    db.execSQL("CREATE INDEX chat_folder_position_index ON chat_folder (position)")
    db.execSQL("CREATE INDEX chat_folder_membership_chat_folder_id_index ON chat_folder_membership (chat_folder_id)")
    db.execSQL("CREATE INDEX chat_folder_membership_thread_id_index ON chat_folder_membership (thread_id)")
    db.execSQL("CREATE INDEX chat_folder_membership_membership_type_index ON chat_folder_membership (membership_type)")

    ChatFolderTables.insertInitialChatFoldersAtCreationTime(db)
  }
}
