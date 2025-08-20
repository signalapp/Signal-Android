package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Base64
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import java.util.UUID

/**
 * Adds four columns chat_folder_id, storage_service_id, storage_service_proto and deleted_timestamp_ms to support chat folders in storage sync.
 * Removes unnecessary is_muted column in chat folders table.
 */
@Suppress("ClassName")
object V270_FixChatFolderColumnsForStorageSync : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE chat_folder DROP COLUMN is_muted")
    db.execSQL("ALTER TABLE chat_folder ADD COLUMN chat_folder_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE chat_folder ADD COLUMN storage_service_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE chat_folder ADD COLUMN storage_service_proto TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE chat_folder ADD COLUMN deleted_timestamp_ms INTEGER DEFAULT 0")

    // Assign all of the folders with a [ChatFolderId] and reset position
    db.rawQuery("SELECT _id FROM chat_folder ORDER BY position ASC")
      .readToList { it.requireLong("_id") }
      .forEachIndexed { index, id -> resetPositionAndSetChatFolderId(db, id, index) }
  }

  private fun resetPositionAndSetChatFolderId(db: SQLiteDatabase, id: Long, newPosition: Int) {
    db.rawQuery(
      """
      UPDATE chat_folder
      SET chat_folder_id = '${UUID.randomUUID()}', position = $newPosition, storage_service_id = '${Base64.encodeWithPadding(StorageSyncHelper.generateKey())}'
      WHERE _id = $id
      """
    )
  }
}
