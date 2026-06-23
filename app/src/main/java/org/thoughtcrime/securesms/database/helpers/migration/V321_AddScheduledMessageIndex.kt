package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V321_AddScheduledMessageIndex : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_scheduled_non_story_index ON message (scheduled_date) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date != -1")
  }
}
