package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V170_CallTableMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE call (
        _id INTEGER PRIMARY KEY,
        call_id INTEGER NOT NULL UNIQUE,
        message_id INTEGER NOT NULL REFERENCES mms (_id) ON DELETE CASCADE,
        peer INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        type INTEGER NOT NULL,
        direction INTEGER NOT NULL,
        event INTEGER NOT NULL
      )
      """.trimIndent()
    )

    db.execSQL("CREATE INDEX call_call_id_index ON call (call_id)")
    db.execSQL("CREATE INDEX call_message_id_index ON call (message_id)")
  }
}
