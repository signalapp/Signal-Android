package org.signal.spinner

import android.database.Cursor

/**
 * An interface to transform on column value into another. Useful for making certain data fields (like bitmasks) more readable.
 */
interface ColumnTransformer {
  /**
   * In certain circumstances (like some queries), the table name may not be guaranteed.
   */
  fun matches(tableName: String?, columnName: String): Boolean

  /**
   * In certain circumstances (like some queries), the table name may not be guaranteed.
   */
  fun transform(tableName: String?, columnName: String, cursor: Cursor): String
}
