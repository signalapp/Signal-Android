package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Removes the is_mms column from the MSL tables and fixes the triggers.
 */
@Suppress("ClassName")
object V177_MessageSendLogTableCleanupMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Delete old stuff
    db.execSQL("DROP TRIGGER IF EXISTS msl_mms_delete")
    db.execSQL("DROP TRIGGER IF EXISTS msl_attachment_delete")
    db.execSQL("DROP INDEX IF EXISTS msl_message_message_index")

    // Cleanup dangling messages
    db.execSQL(
      """
        DELETE FROM msl_payload
        WHERE _id IN (
          SELECT payload_id
          FROM msl_message
          WHERE message_id NOT IN (
            SELECT _id
            FROM message
          )
        )
      """
    )

    // Recreate msl_message table without an is_mms column
    db.execSQL(
      """
        CREATE TABLE msl_message_tmp (
          _id INTEGER PRIMARY KEY,
          payload_id INTEGER NOT NULL REFERENCES msl_payload (_id) ON DELETE CASCADE,
          message_id INTEGER NOT NULL
        )
      """
    )

    db.execSQL(
      """
        INSERT INTO msl_message_tmp
        SELECT
          _id,
          payload_id,
          message_id
        FROM msl_message
      """
    )

    db.execSQL("DROP TABLE msl_message")
    db.execSQL("ALTER TABLE msl_message_tmp RENAME TO msl_message")

    // Recreate the indexes
    db.execSQL("CREATE INDEX msl_message_message_index ON msl_message (message_id, payload_id)")

    // Recreate the triggers
    db.execSQL(
      """
        CREATE TRIGGER msl_message_delete AFTER DELETE ON message 
        BEGIN 
        	DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id);
        END
      """
    )

    db.execSQL(
      """
        CREATE TRIGGER msl_attachment_delete AFTER DELETE ON part
        BEGIN 
        	DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old.mid);
        END
      """
    )
  }
}
