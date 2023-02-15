package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adds support for storing the systemNickname from storage service.
 */
object V180_RecipientNicknameMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN system_nickname TEXT DEFAULT NULL")
  }
}
