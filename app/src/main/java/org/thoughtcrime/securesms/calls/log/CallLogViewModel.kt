package org.thoughtcrime.securesms.calls.log

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import org.signal.paging.ObservablePagedData
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.PagingController
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel for call log management.
 */
class CallLogViewModel(
  private val callLogRepository: CallLogRepository = CallLogRepository()
) : ViewModel() {
  private val callLogStore = RxStore(CallLogState())
  private val pagedData: Observable<ObservablePagedData<CallLogRow.Id, CallLogRow>> = callLogStore
    .stateFlowable
    .toObservable()
    .map { (query, filter) ->
      PagedData.createForObservable(
        CallLogPagedDataSource(query, filter, callLogRepository),
        pagingConfig
      )
    }

  val controller: Observable<PagingController<CallLogRow.Id>> = pagedData.map { it.controller }
  val data: Observable<MutableList<CallLogRow>> = pagedData.switchMap { it.data }
  val selected: Observable<CallLogSelectionState> = callLogStore
    .stateFlowable
    .toObservable()
    .map { it.selectionState }

  val selectionStateSnapshot: CallLogSelectionState
    get() = callLogStore.state.selectionState
  val filterSnapshot: CallLogFilter
    get() = callLogStore.state.filter

  val hasSearchQuery: Boolean
    get() = !callLogStore.state.query.isNullOrBlank()

  private val pagingConfig = PagingConfig.Builder()
    .setBufferPages(1)
    .setPageSize(20)
    .setStartIndex(0)
    .build()

  fun toggleSelected(callId: CallLogRow.Id) {
    callLogStore.update {
      val selectionState = it.selectionState.toggle(callId)
      it.copy(selectionState = selectionState)
    }
  }

  fun clearSelected() {
    callLogStore.update {
      it.copy(selectionState = CallLogSelectionState.empty())
    }
  }

  fun setSearchQuery(query: String) {
    callLogStore.update { it.copy(query = query) }
  }

  fun setFilter(filter: CallLogFilter) {
    callLogStore.update { it.copy(filter = filter) }
  }

  private data class CallLogState(
    val query: String? = null,
    val filter: CallLogFilter = CallLogFilter.ALL,
    val selectionState: CallLogSelectionState = CallLogSelectionState.empty()
  )
}
