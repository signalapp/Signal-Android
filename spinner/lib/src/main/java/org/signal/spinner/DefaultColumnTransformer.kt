package org.signal.spinner

import android.database.Cursor
import android.util.Base64

internal object DefaultColumnTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return true
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String {
    val index = cursor.getColumnIndex(columnName)
    val data: String? = when (cursor.getType(index)) {
      Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(index), 0)
      else -> cursor.getString(index)
    }

    return data ?: "null"
  }
}
