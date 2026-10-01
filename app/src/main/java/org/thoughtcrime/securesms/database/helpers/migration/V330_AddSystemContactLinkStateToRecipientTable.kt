package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds a column for whether a recipient is tied to a system contact: never (0), currently (1), or
 * formerly and needing a new link (2). Every recipient with a system contact URI is currently tied.
 */
@Suppress("ClassName")
object V330_AddSystemContactLinkStateToRecipientTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN system_contact_link_state INTEGER DEFAULT 0")
    db.execSQL("UPDATE recipient SET system_contact_link_state = 1 WHERE system_contact_uri NOT NULL")
  }
}
