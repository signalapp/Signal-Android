package org.thoughtcrime.securesms.calls.log

import org.signal.paging.PagedDataSource

class CallLogPagedDataSource(
  private val query: String?,
  private val filter: CallLogFilter,
  private val repository: CallRepository
) : PagedDataSource<CallLogRow.Id, CallLogRow> {

  private val hasFilter = filter == CallLogFilter.MISSED

  override fun size(): Int {
    return repository.getCallsCount(query, filter) + (if (hasFilter) 1 else 0)
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<CallLogRow> {
    val calls: MutableList<CallLogRow> = repository.getCalls(query, filter, start, length).toMutableList()

    if (calls.size < length && hasFilter) {
      calls.add(CallLogRow.ClearFilter)
    }

    return calls
  }

  override fun getKey(data: CallLogRow): CallLogRow.Id = data.id

  override fun load(key: CallLogRow.Id?): CallLogRow = error("Not supported")

  interface CallRepository {
    fun getCallsCount(query: String?, filter: CallLogFilter): Int
    fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow>
  }
}
