package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.spinner.ColumnTransformer

/**
 * Transforms enum integers for [CollapsedState] into a human readable state
 */
object CollapsedStateTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == MessageTable.COLLAPSED_STATE && (tableName == null || tableName == MessageTable.TABLE_NAME)
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val state = CollapsedState.deserialize(cursor.requireLong(MessageTable.COLLAPSED_STATE))
    return "${cursor.requireInt(MessageTable.COLLAPSED_STATE)}<br><br>$state"
  }
}
