package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a recipient foreign key constraint to notification profile members
 */
object V279_AddNotificationProfileForeignKey : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX IF EXISTS notification_profile_allowed_members_profile_index")

    db.execSQL(
      """
      CREATE TABLE notification_profile_allowed_members_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        notification_profile_id INTEGER NOT NULL REFERENCES notification_profile (_id) ON DELETE CASCADE,
        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        UNIQUE(notification_profile_id, recipient_id) ON CONFLICT REPLACE
      )
      """.trimIndent()
    )

    db.execSQL(
      """
        INSERT INTO notification_profile_allowed_members_tmp (_id, notification_profile_id, recipient_id)
        SELECT 
          notification_profile_allowed_members._id,
          notification_profile_allowed_members.notification_profile_id,
          notification_profile_allowed_members.recipient_id
        FROM notification_profile_allowed_members 
        INNER JOIN recipient ON notification_profile_allowed_members.recipient_id = recipient._id
      """.trimIndent()
    )

    db.execSQL("DROP TABLE notification_profile_allowed_members")
    db.execSQL("ALTER TABLE notification_profile_allowed_members_tmp RENAME TO notification_profile_allowed_members")

    db.execSQL("CREATE INDEX notification_profile_allowed_members_profile_index ON notification_profile_allowed_members (notification_profile_id)")
    db.execSQL("CREATE INDEX notification_profile_allowed_members_recipient_index ON notification_profile_allowed_members (recipient_id)")
  }
}
