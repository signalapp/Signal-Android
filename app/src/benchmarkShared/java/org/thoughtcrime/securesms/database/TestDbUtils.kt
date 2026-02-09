package org.thoughtcrime.securesms.database

import android.content.ContentValues
import org.signal.core.util.SqlUtil.buildArgs

object TestDbUtils {

  fun setMessageReceived(messageId: Long, timestamp: Long) {
    val database: SQLiteDatabase = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    val contentValues = ContentValues()
    contentValues.put(MessageTable.DATE_RECEIVED, timestamp)
    val rowsUpdated = database.update(MessageTable.TABLE_NAME, contentValues, DatabaseTable.ID_WHERE, buildArgs(messageId))
  }
}
