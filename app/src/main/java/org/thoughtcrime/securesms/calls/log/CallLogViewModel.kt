package org.thoughtcrime.securesms.calls.log

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.paging.ObservablePagedData
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel for call log management.
 */
class CallLogViewModel(
  val callLogPeekHelper: CallLogPeekHelper = CallLogPeekHelper(),
  private val callLogRepository: CallLogRepository = CallLogRepository(callLogPeekHelper = callLogPeekHelper)
) : ViewModel() {
  private val callLogStore = RxStore(CallLogState())

  private val disposables = CompositeDisposable()
  private val pagedData: BehaviorProcessor<ObservablePagedData<CallLogRow.Id, CallLogRow>> = BehaviorProcessor.create()

  private val distinctQueryFilterPairs = callLogStore
    .stateFlowable
    .map { (query, filter) -> Pair(query, filter) }
    .distinctUntilChanged()

  val controller = ProxyPagingController<CallLogRow.Id>()
  val data: Flowable<MutableList<CallLogRow?>> = pagedData.switchMap { it.data.toFlowable(BackpressureStrategy.LATEST) }
  val selected: Flowable<CallLogSelectionState> = callLogStore.stateFlowable.map { it.selectionState }

  private val _isEmpty: BehaviorProcessor<Boolean> = BehaviorProcessor.createDefault(false)
  val isEmpty: Boolean get() = _isEmpty.value ?: false

  val totalCount: Flowable<Int> = Flowable.combineLatest(distinctQueryFilterPairs, data) { a, _ -> a }
    .map { (query, filter) -> callLogRepository.getCallsCount(query, filter) }
    .doOnNext { _isEmpty.onNext(it <= 0) }

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

  init {
    disposables.add(callLogStore)
    disposables += distinctQueryFilterPairs.subscribe { (query, filter) ->
      pagedData.onNext(
        PagedData.createForObservable(
          CallLogPagedDataSource(query, filter, callLogRepository),
          pagingConfig
        )
      )
    }

    disposables += pagedData.map { it.controller }.subscribe {
      controller.set(it)
    }

    disposables += callLogRepository.listenForChanges().subscribe {
      callLogPeekHelper.onDataSetInvalidated()
      controller.onDataInvalidated()
    }

    disposables += AppDependencies
      .signalCallManager
      .peekInfoCache
      .observeOn(Schedulers.computation())
      .distinctUntilChanged()
      .subscribe {
        callLogPeekHelper.onDataSetInvalidated()
        controller.onDataInvalidated()
      }
  }

  override fun onCleared() {
    disposables.dispose()
  }

  fun markAllCallEventsRead() {
    callLogRepository.markAllCallEventsRead()
  }

  fun selectAll() {
    callLogStore.update {
      val selectionState = CallLogSelectionState.selectAll()
      it.copy(selectionState = selectionState)
    }
  }

  fun toggleSelected(callId: CallLogRow.Id) {
    callLogStore.update {
      val selectionState = it.selectionState.toggle(callId)
      it.copy(selectionState = selectionState)
    }
  }

  @MainThread
  fun stageCallDeletion(call: CallLogRow): CallLogStagedDeletion {
    return CallLogStagedDeletion(
      callLogStore.state.filter,
      CallLogSelectionState.empty().toggle(call.id),
      callLogRepository
    )
  }

  @MainThread
  fun stageSelectionDeletion(): CallLogStagedDeletion {
    return CallLogStagedDeletion(
      callLogStore.state.filter,
      callLogStore.state.selectionState,
      callLogRepository
    )
  }

  fun stageDeleteAll(): CallLogStagedDeletion {
    callLogStore.update {
      it.copy(
        selectionState = CallLogSelectionState.empty()
      )
    }

    return CallLogStagedDeletion(
      callLogStore.state.filter,
      CallLogSelectionState.selectAll(),
      callLogRepository
    )
  }

  @SuppressLint("CheckResult")
  fun delete(stagedDeletion: CallLogStagedDeletion): Maybe<CallLogDeletionResult> {
    return stagedDeletion.commit()
      .doOnSubscribe {
        clearSelected()
      }
      .map { failedRevocations ->
        if (failedRevocations == 0) {
          CallLogDeletionResult.Success
        } else {
          CallLogDeletionResult.FailedToRevoke(failedRevocations)
        }
      }
      .onErrorReturn { CallLogDeletionResult.UnknownFailure(it) }
      .toMaybe()
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
