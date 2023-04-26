package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable

/**
 * Adds a new 'timestamp' column to CallTable and copies in the date_sent column data from
 * the messages database.
 *
 * Adds a new 'ringer' column to the CallTable setting each entry to NULL. This is safe since up
 * to this point we were not using the table for group calls. This is effectively a replacement for
 * the GroupCallRing table.
 *
 * Removes the 'NOT NULL' condition on message_id and peer, as with ad-hoc calling in place, these
 * can now be null.
 */
object V182_CallTableMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE call_tmp (
        _id INTEGER PRIMARY KEY,
        call_id INTEGER NOT NULL UNIQUE,
        message_id INTEGER DEFAULT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE SET NULL,
        peer INTEGER DEFAULT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        type INTEGER NOT NULL,
        direction INTEGER NOT NULL,
        event INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        ringer INTEGER DEFAULT NULL,
        deletion_timestamp INTEGER DEFAULT 0
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO call_tmp
      SELECT
          _id,
          call_id,
          message_id,
          peer,
          type,
          direction,
          event,
          (SELECT date_sent FROM message WHERE message._id = call.message_id) as timestamp,
          NULL as ringer,
          0 as deletion_timestamp
      FROM call
      """.trimIndent()
    )

    db.execSQL("DROP TABLE group_call_ring")
    db.execSQL("DROP TABLE call")
    db.execSQL("ALTER TABLE call_tmp RENAME TO call")
  }
}
