package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the columns and indexes necessary for collapsing updates
 */
@Suppress("ClassName")
object V313_AddCollapsingUpdateColumns : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN collapsed_state INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE message ADD COLUMN collapsed_head_id INTEGER DEFAULT 0")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_collapsed_state_index ON message (collapsed_state)")
    db.execSQL("CREATE INDEX message_collapsed_head_id_index ON message (collapsed_head_id)")

    // Adjust existing index to disregard collapsed updates from the thread count
    db.execSQL("DROP INDEX IF EXISTS message_thread_count_index")
    db.execSQL("CREATE INDEX message_thread_count_index ON message (thread_id) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND latest_revision_id IS NULL AND collapsed_state != 3")
  }
}
