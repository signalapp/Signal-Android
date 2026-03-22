package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a partial index on attachment (data_size, display_order) to speed up
 * the "all threads" media overview sorted by largest.
 */
@Suppress("ClassName")
object V311_AddAttachmentMediaOverviewSizeIndex : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_media_overview_size ON attachment (data_size DESC, display_order DESC) WHERE quote = 0 AND sticker_pack_id IS NULL AND data_file IS NOT NULL")
  }
}
