package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V319_AddAttachmentAndMessageIndexes : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_expire_started_index ON message (expire_started) WHERE expire_started > 0")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_view_once_index ON message (view_once) WHERE view_once > 0")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_archive_thumbnail_transfer_state ON attachment (archive_thumbnail_transfer_state)")
  }
}
