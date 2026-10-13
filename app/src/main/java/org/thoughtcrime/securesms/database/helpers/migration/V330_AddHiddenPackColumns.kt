package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/** Adds columns to support hiding a sticker pack */
@Suppress("ClassName")
object V330_AddHiddenPackColumns : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN hidden INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN unhidden_timestamp_ms INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN hidden_storage_service_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN hidden_storage_service_proto TEXT DEFAULT NULL")
  }
}
