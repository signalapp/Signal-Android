package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds column to messages to track who has deleted a given message. Because of an
 * OOM crash, we do not drop the remote_deleted column. For users who already completed
 * this migration, we add it back in the future.
 */
@Suppress("ClassName")
object V302_AddDeletedByColumn : SignalDatabaseMigration {

  private val TAG = Log.tag(V302_AddDeletedByColumn::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqlUtil.columnExists(db, "message", "deleted_by")) {
      Log.i(TAG, "Already ran migration!")
      return
    }

    val stopwatch = Stopwatch("migration", decimalPlaces = 2)

    db.execSQL("ALTER TABLE message ADD COLUMN deleted_by INTEGER DEFAULT NULL REFERENCES recipient (_id) ON DELETE CASCADE")
    stopwatch.split("add-column")

    db.execSQL("UPDATE message SET deleted_by = from_recipient_id WHERE remote_deleted > 0")
    stopwatch.split("update-data")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_deleted_by_index ON message (deleted_by)")
    stopwatch.split("create-index")

    stopwatch.stop(TAG)
  }
}
