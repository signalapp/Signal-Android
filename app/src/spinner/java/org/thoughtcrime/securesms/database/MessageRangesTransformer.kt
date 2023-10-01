package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireBlob
import org.signal.spinner.ColumnTransformer
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList

object MessageRangesTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == MessageTable.MESSAGE_RANGES && (tableName == null || tableName == MessageTable.TABLE_NAME)
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val messageRangesData: ByteArray? = cursor.requireBlob(MessageTable.MESSAGE_RANGES)

    return if (messageRangesData != null) {
      val ranges = BodyRangeList.ADAPTER.decode(messageRangesData)
      ranges.ranges
        .take(5)
        .map { range ->
          val mention = range.mentionUuid != null
          val style = range.style != null
          val start = range.start
          val length = range.length

          var rangeString = "<br>Type: ${if (mention) "mention" else "style"}<br>-start: $start<br>-length: $length"

          if (mention) {
            rangeString += "<br>-uuid: ${range.mentionUuid}"
          }

          if (style) {
            rangeString += "<br>-style: ${range.style}"
          }

          rangeString
        }.joinToString("<br>")
        .let {
          if (ranges.ranges.size > 5) {
            it + "<br>" + "Not showing an additional ${ranges.ranges.size - 5} body ranges."
          } else {
            it
          }
        }
    } else {
      null
    }
  }
}
