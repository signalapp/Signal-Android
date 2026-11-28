package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/** Adds columns to support favorite stickers and syncing to storage service */
@Suppress("ClassName")
object V329_AddStickerFavoriteColumns : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE sticker ADD COLUMN favorited_at INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sticker ADD COLUMN unfavorited_at INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sticker ADD COLUMN storage_service_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE sticker ADD COLUMN storage_service_proto TEXT DEFAULT NULL")
  }
}
