package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.spinner.ColumnTransformer

object RecipientTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return tableName == RecipientTable.TABLE_NAME && columnName == RecipientTable.TYPE
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    return RecipientTable.RecipientType.fromId(cursor.requireInt(RecipientTable.TYPE)).toString()
  }
}
