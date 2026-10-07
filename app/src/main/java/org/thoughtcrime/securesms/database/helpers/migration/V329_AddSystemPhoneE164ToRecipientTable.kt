package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column for the recipient's number as last seen on their linked system contact, so a change
 * to the recipient's number no longer counts as being in the address book.
 *
 * Every existing link was made by matching the recipient's current number, so it is filled from
 * that.
 */
@Suppress("ClassName")
object V329_AddSystemPhoneE164ToRecipientTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN system_phone_e164 TEXT DEFAULT NULL")
    db.execSQL("UPDATE recipient SET system_phone_e164 = e164 WHERE system_contact_uri NOT NULL")
  }
}
