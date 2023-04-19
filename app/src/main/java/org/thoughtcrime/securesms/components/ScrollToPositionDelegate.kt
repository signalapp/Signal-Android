package org.thoughtcrime.securesms.components

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.doAfterNextLayout
import kotlin.math.max

/**
 * Delegate object to help manage scroll position requests.
 *
 * @param recyclerView The recycler view that will be scrolled
 * @param canJumpToPosition Allows additional checks to see if we can scroll. For example, PagingMappingAdapter#isAvailableAround
 * @param mapToTruePosition Allows additional offsets to be applied to the position.
 */
class ScrollToPositionDelegate private constructor(
  private val recyclerView: RecyclerView,
  canJumpToPosition: (Int) -> Boolean,
  mapToTruePosition: (Int) -> Int,
  disposables: CompositeDisposable
) : Disposable by disposables {
  companion object {
    private val TAG = Log.tag(ScrollToPositionDelegate::class.java)
    const val NO_POSITION = -1
    private val EMPTY = ScrollToPositionRequest(NO_POSITION, true)
    private const val SMOOTH_SCROLL_THRESHOLD = 25
  }

  private val listCommitted = BehaviorSubject.create<Unit>()
  private val scrollPositionRequested = BehaviorSubject.createDefault(EMPTY)
  private val scrollPositionRequests: Observable<ScrollToPositionRequest> = Observable.combineLatest(listCommitted, scrollPositionRequested) { _, b -> b }

  constructor(
    recyclerView: RecyclerView,
    canJumpToPosition: (Int) -> Boolean = { true },
    mapToTruePosition: (Int) -> Int = { it }
  ) : this(recyclerView, canJumpToPosition, mapToTruePosition, CompositeDisposable())

  init {
    disposables += scrollPositionRequests
      .observeOn(AndroidSchedulers.mainThread())
      .filter { it.position >= 0 && canJumpToPosition(it.position) }
      .map { it.copy(position = mapToTruePosition(it.position)) }
      .subscribeBy(onNext = { position ->
        recyclerView.doAfterNextLayout {
          handleScrollPositionRequest(position, recyclerView)
        }

        if (!(recyclerView.isLayoutRequested || recyclerView.isInLayout)) {
          recyclerView.requestLayout()
        }
      })
  }

  /**
   * Entry point for requesting a specific scroll position.
   */
  fun requestScrollPosition(position: Int, smooth: Boolean = true) {
    scrollPositionRequested.onNext(ScrollToPositionRequest(position, smooth))
  }

  /**
   * Reset the scroll position to 0
   */
  fun resetScrollPosition() {
    requestScrollPosition(0, true)
  }

  /**
   * This should be called every time a list is submitted to the RecyclerView's adapter.
   */
  fun notifyListCommitted() {
    listCommitted.onNext(Unit)
  }

  fun isListCommitted(): Boolean = listCommitted.value != null

  private fun handleScrollPositionRequest(
    request: ScrollToPositionRequest,
    recyclerView: RecyclerView
  ) {
    requestScrollPosition(NO_POSITION, false)

    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
    if (layoutManager == null) {
      Log.w(TAG, "Layout manager is not set or of an invalid type.")
      return
    }

    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
      return
    }

    val position = max(0, request.position - 1)
    val offset = when {
      position == 0 -> 0
      layoutManager.reverseLayout -> recyclerView.height
      else -> 0
    }

    Log.d(TAG, "Scrolling to position $position with offset $offset.")

    if (request.smooth && position == 0 && layoutManager.findFirstVisibleItemPosition() < SMOOTH_SCROLL_THRESHOLD) {
      recyclerView.smoothScrollToPosition(position)
    } else {
      layoutManager.scrollToPositionWithOffset(position, offset)
    }
  }

  private data class ScrollToPositionRequest(
    val position: Int,
    val smooth: Boolean
  )
}
