package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireLong
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import org.thoughtcrime.securesms.util.toLocalDateTime
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime

object TimestampTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName.contains("date", true) ||
      columnName.contains("timestamp", true) ||
      columnName.contains("created_at", true) ||
      columnName.endsWith("time", true)
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val timestamp: Long = cursor.requireLong(columnName)

    return if (timestamp > LocalDateTime.of(2000, 1, 1, 0, 0, 0, 0).toMillis()) {
      "$timestamp<br><br>${timestamp.toLocalDateTime()}"
    } else {
      DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }
  }
}
