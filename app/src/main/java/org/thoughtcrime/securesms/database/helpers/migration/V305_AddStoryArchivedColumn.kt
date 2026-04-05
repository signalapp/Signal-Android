package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a story_archived column to the message table so that outgoing stories
 * can be preserved in an archive after they leave the 24-hour active feed.
 */
@Suppress("ClassName")
object V305_AddStoryArchivedColumn : SignalDatabaseMigration {

  private val TAG = Log.tag(V305_AddStoryArchivedColumn::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN story_archived INTEGER DEFAULT 0")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_story_archived_index ON message (story_archived, story_type, date_sent) WHERE story_type > 0 AND story_archived > 0")
    Log.i(TAG, "Added story_archived column and index.")
  }
}
