package org.signal.spinner

import android.database.Cursor
import org.signal.core.util.Base64

object DefaultColumnTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return true
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val index = cursor.getColumnIndex(columnName)
    return when (cursor.getType(index)) {
      Cursor.FIELD_TYPE_BLOB -> Base64.encodeWithPadding(cursor.getBlob(index))
      else -> cursor.getString(index)
    }
  }
}
