package org.thoughtcrime.securesms.components

import android.view.View
import androidx.annotation.AnyThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.doAfterNextLayout
import kotlin.math.abs
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
  private val disposables: CompositeDisposable
) : Disposable by disposables {
  companion object {
    private val TAG = Log.tag(ScrollToPositionDelegate::class.java)
    const val NO_POSITION = -1
    private const val SMOOTH_SCROLL_THRESHOLD = 25
    private const val SCROLL_ANIMATION_THRESHOLD = 50
    private val EMPTY = ScrollToPositionRequest(
      position = NO_POSITION,
      smooth = true,
      scrollStrategy = DefaultScrollStrategy
    )
  }

  private val listCommitted = BehaviorSubject.create<Long>()
  private val scrollPositionRequested = BehaviorSubject.createDefault(EMPTY)
  private val scrollPositionRequests: Observable<ScrollToPositionRequest> = Observable.combineLatest(listCommitted, scrollPositionRequested) { _, b -> b }

  private var markedListCommittedTimestamp: Long = 0L

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
   *
   * @param position The desired position to jump to. -1 to clear the current request.
   * @param smooth Whether a smooth scroll will be attempted. Only done if we are within a certain distance.
   * @param scrollStrategy See [ScrollStrategy]
   */
  @AnyThread
  fun requestScrollPosition(
    position: Int,
    smooth: Boolean = true,
    scrollStrategy: ScrollStrategy = DefaultScrollStrategy
  ) {
    scrollPositionRequested.onNext(ScrollToPositionRequest(position, smooth, scrollStrategy))
  }

  /**
   * Reset the scroll position to 0
   */
  @AnyThread
  fun resetScrollPosition() {
    requestScrollPosition(0, true)
  }

  /**
   * Reset the scroll position to 0 after a list update is committed that occurs later
   * than the version set by [markListCommittedVersion].
   */
  @AnyThread
  fun resetScrollPositionAfterMarkListVersionSurpassed() {
    val currentMark = markedListCommittedTimestamp
    listCommitted
      .observeOn(AndroidSchedulers.mainThread())
      .filter { it > currentMark }
      .firstElement()
      .subscribeBy {
        requestScrollPosition(0, true)
      }
      .addTo(disposables)
  }

  /**
   * This should be called every time a list is submitted to the RecyclerView's adapter.
   */
  @AnyThread
  fun notifyListCommitted() {
    listCommitted.onNext(System.currentTimeMillis())
  }

  fun isListCommitted(): Boolean = listCommitted.value != null

  fun markListCommittedVersion() {
    markedListCommittedTimestamp = listCommitted.value ?: 0L
  }

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

    val position = max(0, request.position)

    request.scrollStrategy.performScroll(
      recyclerView,
      layoutManager,
      position,
      request.smooth
    )
  }

  private data class ScrollToPositionRequest(
    val position: Int,
    val smooth: Boolean,
    val scrollStrategy: ScrollStrategy
  )

  /**
   * Jumps to the desired position, pinning it to the "top" of the recycler.
   */
  object DefaultScrollStrategy : ScrollStrategy {
    override fun performScroll(
      recyclerView: RecyclerView,
      layoutManager: LinearLayoutManager,
      position: Int,
      smooth: Boolean
    ) {
      val offset = when {
        position == 0 -> 0
        layoutManager.reverseLayout -> recyclerView.height
        else -> 0
      }

      Log.d(TAG, "Scrolling to $position")

      if (smooth && position == 0 && layoutManager.findFirstVisibleItemPosition() < SMOOTH_SCROLL_THRESHOLD) {
        recyclerView.smoothScrollToPosition(position)
      } else {
        layoutManager.scrollToPositionWithOffset(position, offset)
      }
    }
  }

  /**
   * Jumps to the given position but tries to ensure that the contents are completely visible on screen.
   */
  object JumpToPositionStrategy : ScrollStrategy {
    override fun performScroll(recyclerView: RecyclerView, layoutManager: LinearLayoutManager, position: Int, smooth: Boolean) {
      if (abs(layoutManager.findFirstVisibleItemPosition() - position) < SCROLL_ANIMATION_THRESHOLD) {
        val child: View? = layoutManager.findViewByPosition(position)
        if (child == null || !layoutManager.isViewPartiallyVisible(child, true, false)) {
          layoutManager.scrollToPositionWithOffset(position, recyclerView.height / 3)
        }
      } else {
        layoutManager.scrollToPositionWithOffset(position, recyclerView.height / 3)
      }
    }
  }

  /**
   * Performs the actual scrolling for a given request.
   */
  interface ScrollStrategy {
    /**
     * @param recyclerView The recycler view which is to be scrolled
     * @param layoutManager The typed layout manager attached to the recycler view
     * @param position The position we should scroll to.
     * @param smooth Whether or not a smooth scroll should be attempted
     */
    fun performScroll(
      recyclerView: RecyclerView,
      layoutManager: LinearLayoutManager,
      position: Int,
      smooth: Boolean
    )
  }
}
