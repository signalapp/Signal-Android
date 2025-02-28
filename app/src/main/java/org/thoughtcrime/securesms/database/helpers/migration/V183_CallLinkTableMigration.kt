package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the CallLinkTable and modifies the CallTable to include an FK into it.
 */
@Suppress("ClassName")
object V183_CallLinkTableMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE TABLE call_link (_id INTEGER PRIMARY KEY)")

    db.execSQL(
      """
      CREATE TABLE call_tmp (
        _id INTEGER PRIMARY KEY,
        call_id INTEGER NOT NULL,
        message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE SET NULL,
        peer INTEGER DEFAULT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        call_link INTEGER DEFAULT NULL REFERENCES call_link (_id) ON DELETE CASCADE,
        type INTEGER NOT NULL,
        direction INTEGER NOT NULL,
        event INTEGER NOT NULL,
        timestamp INTEGER NOT NULL,
        ringer INTEGER DEFAULT NULL,
        deletion_timestamp INTEGER DEFAULT 0,
        UNIQUE (_id, peer, call_link) ON CONFLICT FAIL,
        CHECK ((peer IS NULL AND call_link IS NOT NULL) OR (peer IS NOT NULL AND call_link IS NULL))
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
        NULL as call_link,
        type,
        direction,
        event,
        timestamp,
        ringer,
        deletion_timestamp
      FROM call
      """.trimIndent()
    )

    db.execSQL("DROP TABLE call")
    db.execSQL("ALTER TABLE call_tmp RENAME TO call")
  }
}
