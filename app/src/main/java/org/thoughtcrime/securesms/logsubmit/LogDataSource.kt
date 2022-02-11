package org.thoughtcrime.securesms.logsubmit

import android.app.Application
import org.signal.core.util.logging.Scrubber
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.database.LogDatabase
import java.lang.UnsupportedOperationException

/**
 * Retrieves logs to show in the [SubmitDebugLogActivity].
 *
 * @param prefixLines A static list of lines to show before all of the lines retrieved from [LogDatabase]
 * @param untilTime Only show logs before this time. This is our way of making sure the set of logs we show on this screen doesn't grow.
 */
class LogDataSource(
  application: Application,
  private val prefixLines: List<LogLine>,
  private val untilTime: Long
) :
  PagedDataSource<Long, LogLine> {

  val logDatabase = LogDatabase.getInstance(application)

  override fun size(): Int {
    return prefixLines.size + logDatabase.getLogCountBeforeTime(untilTime)
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): List<LogLine> {
    if (start + length < prefixLines.size) {
      return prefixLines.subList(start, start + length)
    } else if (start < prefixLines.size) {
      return prefixLines.subList(start, prefixLines.size) +
        logDatabase.getRangeBeforeTime(0, length - (prefixLines.size - start), untilTime).map { convertToLogLine(it) }
    } else {
      return logDatabase.getRangeBeforeTime(start - prefixLines.size, length, untilTime).map { convertToLogLine(it) }
    }
  }

  override fun load(key: Long?): LogLine? {
    throw UnsupportedOperationException("Not implemented!")
  }

  override fun getKey(data: LogLine): Long {
    return data.id
  }

  private fun convertToLogLine(raw: String): LogLine {
    val scrubbed: String = Scrubber.scrub(raw).toString()
    return SimpleLogLine(scrubbed, LogStyleParser.parseStyle(scrubbed), LogLine.Placeholder.NONE)
  }
}
