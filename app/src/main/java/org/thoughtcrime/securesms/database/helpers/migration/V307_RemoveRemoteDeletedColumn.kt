package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Attempts to remove the remote_deleted column again but in its own isolated change
 */
@Suppress("ClassName")
object V307_RemoveRemoteDeletedColumn : SignalDatabaseMigration {

  private val TAG = Log.tag(V307_RemoveRemoteDeletedColumn::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val start = System.currentTimeMillis()
    if (!SqlUtil.columnExists(db, "message", "remote_deleted")) {
      Log.i(TAG, "Does not have remote_deleted column!")
      return
    }

    db.execSQL("ALTER TABLE message DROP COLUMN remote_deleted")
    Log.i(TAG, "Dropping remote_deleted column, took ${System.currentTimeMillis() - start}ms")
  }
}
