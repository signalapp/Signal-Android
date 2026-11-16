package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/** Adds columns for a name supplied by a third party through a shared contact card. */
@Suppress("ClassName")
object V328_AddSharedNameToRecipientTable : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN shared_given_name TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE recipient ADD COLUMN shared_family_name TEXT DEFAULT NULL")
  }
}
