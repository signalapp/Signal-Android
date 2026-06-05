package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V314_FixMessageRequestAcceptedToRecipient : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE message
      SET to_recipient_id = (SELECT thread.recipient_id FROM thread WHERE thread._id = message.thread_id)
      WHERE (type & 0xF00000000) = 0x600000000
        AND to_recipient_id = from_recipient_id
        AND thread_id IN (SELECT _id FROM thread)
      """
    )
  }
}
