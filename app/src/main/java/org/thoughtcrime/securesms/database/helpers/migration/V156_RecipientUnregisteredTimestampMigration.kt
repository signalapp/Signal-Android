package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.update
import java.util.concurrent.TimeUnit

/**
 * Adds an 'unregistered timestamp' on a recipient to keep track of when they became unregistered.
 * Also updates all currently-unregistered users to have an unregistered time of "now".
 */
object V156_RecipientUnregisteredTimestampMigration : SignalDatabaseMigration {

  const val UNREGISTERED = 2
  const val GROUP_NONE = 0

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN unregistered_timestamp INTEGER DEFAULT 0")

    // We currently delete from storage service after 30 days, so initialize time to 31 days ago.
    // Unregistered users won't have a storageId to begin with, so it won't affect much -- just want all unregistered users to have a timestamp populated.
    val expiredTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)
    db.update("recipient")
      .values("unregistered_timestamp" to expiredTime)
      .where("registered = ? AND group_type = ?", UNREGISTERED, GROUP_NONE)
      .run()
  }
}
