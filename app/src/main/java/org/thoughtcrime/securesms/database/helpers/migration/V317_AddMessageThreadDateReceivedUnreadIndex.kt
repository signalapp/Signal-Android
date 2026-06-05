package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a partial index on message (thread_id, date_received) containing only unread/unseen rows. This lets the mark-thread-read query in
 * MessageTable.setMessagesReadSince seek straight to the relevant rows instead of scanning the whole thread.
 */
@Suppress("ClassName")
object V317_AddMessageThreadDateReceivedUnreadIndex : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_thread_date_received_unread_index ON message (thread_id, date_received) WHERE story_type = 0 AND parent_story_id <= 0 AND (read = 0 OR reactions_unread = 1 OR votes_unread = 1)")
  }
}
