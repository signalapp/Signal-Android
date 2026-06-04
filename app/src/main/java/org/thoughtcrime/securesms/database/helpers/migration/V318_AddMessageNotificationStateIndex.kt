package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Fix bad notified state across the message table so that we can use an index to improve query performance
 * when fetching notification state.
 */
@Suppress("ClassName")
object V318_AddMessageNotificationStateIndex : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val outgoingBaseTypes = "(2, 11, 21, 22, 23, 24, 25, 26, 28)"
    db.execSQL("UPDATE message SET reactions_unread = 0 WHERE reactions_unread = 1 AND (type & 31) NOT IN $outgoingBaseTypes")
    db.execSQL("UPDATE message SET votes_unread = 0 WHERE votes_unread = 1 AND (type & 31) NOT IN $outgoingBaseTypes")

    db.execSQL("UPDATE message SET notified = 1 WHERE notified = 0 AND read = 1 AND reactions_unread = 0 AND votes_unread = 0")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_notification_state_index ON message (date_received) WHERE notified = 0 AND story_type = 0 AND latest_revision_id IS NULL")
  }
}
