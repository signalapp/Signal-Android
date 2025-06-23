package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Introduces the tables and fields required to keep track of whether we need to send a PNI signature message and if the ones we've sent out have been received.
 */
@Suppress("ClassName")
object V154_PniSignaturesMigration : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN needs_pni_signature")

    db.execSQL(
      """
      CREATE TABLE pending_pni_signature_message (
        _id INTEGER PRIMARY KEY,
        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        sent_timestamp INTEGER NOT NULL,
        device_id INTEGER NOT NULL
      )
      """.trimIndent()
    )

    db.execSQL("CREATE UNIQUE INDEX pending_pni_recipient_sent_device_index ON pending_pni_signature_message (recipient_id, sent_timestamp, device_id)")
  }
}
