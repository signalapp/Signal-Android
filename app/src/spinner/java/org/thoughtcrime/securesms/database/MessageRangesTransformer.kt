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
      val ranges = BodyRangeList.parseFrom(messageRangesData)
      ranges.rangesList
        .take(5)
        .map { range ->
          val mention = range.hasMentionUuid()
          val style = range.hasStyle()
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
          if (ranges.rangesCount > 5) {
            it + "<br>" + "Not showing an additional ${ranges.rangesCount - 5} body ranges."
          } else {
            it
          }
        }
    } else {
      null
    }
  }
}
