package org.thoughtcrime.securesms.calls.log

import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.util.FeatureFlags

class CallLogPagedDataSource(
  private val query: String?,
  private val filter: CallLogFilter,
  private val repository: CallRepository
) : PagedDataSource<CallLogRow.Id, CallLogRow> {

  private val hasFilter = filter == CallLogFilter.MISSED
  private val hasCallLinkRow = FeatureFlags.adHocCalling() && filter == CallLogFilter.ALL && query.isNullOrEmpty()

  private var callsCount = 0

  override fun size(): Int {
    callsCount = repository.getCallsCount(query, filter)
    return callsCount + hasFilter.toInt() + hasCallLinkRow.toInt()
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<CallLogRow> {
    val calls = mutableListOf<CallLogRow>()
    val callLimit = length - hasCallLinkRow.toInt()

    if (start == 0 && length >= 1 && hasCallLinkRow) {
      calls.add(CallLogRow.CreateCallLink)
    }

    calls.addAll(repository.getCalls(query, filter, start, callLimit).toMutableList())

    if (calls.size < length && hasFilter) {
      calls.add(CallLogRow.ClearFilter)
    }

    return calls
  }

  override fun getKey(data: CallLogRow): CallLogRow.Id = data.id

  override fun load(key: CallLogRow.Id?): CallLogRow = error("Not supported")

  private fun Boolean.toInt(): Int {
    return if (this) 1 else 0
  }

  interface CallRepository {
    fun getCallsCount(query: String?, filter: CallLogFilter): Int
    fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow>
  }
}
