package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Forgot to recreate the triggers for the reactions table in [V166_ThreadAndMessageForeignKeys]. So we gotta fix stuff up and do it here.
 */
@Suppress("ClassName")
object V167_RecreateReactionTriggers : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
        DELETE FROM reaction
        WHERE 
          (is_mms = 0 AND message_id NOT IN (SELECT _id FROM sms))
          OR
          (is_mms = 1 AND message_id NOT IN (SELECT _id FROM mms))
      """
    )

    db.execSQL(
      """
        CREATE TRIGGER IF NOT EXISTS reactions_sms_delete AFTER DELETE ON sms 
        BEGIN 
        	DELETE FROM reaction WHERE message_id = old._id AND is_mms = 0;
        END
      """
    )

    db.execSQL(
      """
        CREATE TRIGGER IF NOT EXISTS reactions_mms_delete AFTER DELETE ON mms 
        BEGIN 
        	DELETE FROM reaction WHERE message_id = old._id AND is_mms = 1;
        END
      """
    )
  }
}
