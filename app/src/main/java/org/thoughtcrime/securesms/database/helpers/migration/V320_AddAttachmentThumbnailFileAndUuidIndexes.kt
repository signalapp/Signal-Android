package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V320_AddAttachmentThumbnailFileAndUuidIndexes : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_thumbnail_file_index ON attachment (thumbnail_file) WHERE thumbnail_file IS NOT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_uuid_index ON attachment (attachment_uuid) WHERE attachment_uuid IS NOT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_rate_limited_index ON message (_id) WHERE (type & 128) != 0")
  }
}
