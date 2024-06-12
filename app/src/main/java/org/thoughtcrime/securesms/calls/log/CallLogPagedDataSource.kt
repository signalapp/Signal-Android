package org.thoughtcrime.securesms.calls.log

import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.util.RemoteConfig

class CallLogPagedDataSource(
  private val query: String?,
  private val filter: CallLogFilter,
  private val repository: CallRepository
) : PagedDataSource<CallLogRow.Id, CallLogRow> {

  private val hasFilter = filter == CallLogFilter.MISSED
  private val hasCallLinkRow = RemoteConfig.adHocCalling && filter == CallLogFilter.ALL && query.isNullOrEmpty()

  private var callEventsCount = 0
  private var callLinksCount = 0

  override fun size(): Int {
    callEventsCount = repository.getCallsCount(query, filter)
    callLinksCount = repository.getCallLinksCount(query, filter)
    return callEventsCount + callLinksCount + hasFilter.toInt() + hasCallLinkRow.toInt()
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<CallLogRow> {
    val callLogRows = mutableListOf<CallLogRow>()
    if (length <= 0) {
      return callLogRows
    }

    val callLinkStart = if (hasCallLinkRow) 1 else 0
    val callEventStart = callLinkStart + callLinksCount
    val clearFilterStart = callEventStart + callEventsCount

    var remaining = length
    if (start < callLinkStart) {
      callLogRows.add(CallLogRow.CreateCallLink)
      remaining -= 1
    }

    if (start < callEventStart && remaining > 0) {
      val callLinks = repository.getCallLinks(
        query,
        filter,
        start,
        remaining
      )

      callLogRows.addAll(callLinks)

      remaining -= callLinks.size
    }

    if (start < clearFilterStart && remaining > 0) {
      val callEvents = repository.getCalls(
        query,
        filter,
        start - callLinksCount,
        remaining
      )

      callLogRows.addAll(callEvents)

      remaining -= callEvents.size
    }

    if (hasFilter && start <= clearFilterStart && remaining > 0) {
      callLogRows.add(CallLogRow.ClearFilter)
    }

    repository.onCallTabPageLoaded(callLogRows)
    return callLogRows
  }

  override fun getKey(data: CallLogRow): CallLogRow.Id = data.id

  override fun load(key: CallLogRow.Id?): CallLogRow = error("Not supported")

  private fun Boolean.toInt(): Int {
    return if (this) 1 else 0
  }

  interface CallRepository {
    fun getCallsCount(query: String?, filter: CallLogFilter): Int
    fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow>
    fun getCallLinksCount(query: String?, filter: CallLogFilter): Int
    fun getCallLinks(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow>
    fun onCallTabPageLoaded(pageData: List<CallLogRow>)
  }
}
