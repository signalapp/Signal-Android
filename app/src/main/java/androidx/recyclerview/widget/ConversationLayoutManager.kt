/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package androidx.recyclerview.widget

import android.content.Context
import org.signal.core.util.logging.Log

/**
 * Variation of a vertical, reversed [LinearLayoutManager] that makes specific assumptions in how it will
 * be used by Conversation view to support easier scrolling to the initial start position.
 *
 * Primarily, it assumes that an initial scroll to position call will always happen and that the implementation
 * of [LinearLayoutManager] remains unchanged with respect to how it assigns [mPendingScrollPosition] and
 * [mPendingScrollPositionOffset] in [LinearLayoutManager.scrollToPositionWithOffset] and how it always clears
 * the pending state variables in every call to [LinearLayoutManager.onLayoutCompleted].
 *
 * The assumptions are necessary to force the requested scroll position/layout to occur even if the request
 * happens prior to the data source populating the recycler view/adapter.
 */
class ConversationLayoutManager(context: Context) : LinearLayoutManager(context, RecyclerView.VERTICAL, true) {

  private var afterScroll: (() -> Unit)? = null

  override fun supportsPredictiveItemAnimations(): Boolean {
    return false
  }

  /**
   * Scroll to the desired position and be notified when the layout manager has completed the request
   * via [afterScroll] callback.
   */
  fun scrollToPositionWithOffset(position: Int, offset: Int, afterScroll: () -> Unit) {
    this.afterScroll = afterScroll
    super.scrollToPositionWithOffset(position, offset)
  }

  /**
   * If a scroll to position request is made and a layout pass occurs prior to the list being populated with via the data source,
   * the base implementation clears the request as if it was never made.
   *
   * This override will capture the pending scroll position and offset, determine if the scroll request was satisfied, and
   * re-request the scroll to position to force another attempt if not satisfied.
   *
   * A pending scroll request will be re-requested if the pending scroll position is outside the bounds of the current known size of
   * items in the list.
   */
  override fun onLayoutCompleted(state: RecyclerView.State?) {
    val pendingScrollPosition = mPendingScrollPosition
    val pendingScrollOffset = mPendingScrollPositionOffset

    val reRequestPendingPosition = pendingScrollPosition >= (state?.mItemCount ?: 0)

    // Base implementation always clears mPendingScrollPosition+mPendingScrollPositionOffset
    super.onLayoutCompleted(state)

    // Re-request scroll to position request if necessary thus forcing mPendingScrollPosition+mPendingScrollPositionOffset to be re-assigned
    if (reRequestPendingPosition) {
      Log.d(TAG, "Re-requesting pending scroll position: $pendingScrollPosition offset: $pendingScrollOffset")
      if (pendingScrollOffset != INVALID_OFFSET) {
        scrollToPositionWithOffset(pendingScrollPosition, pendingScrollOffset)
      } else {
        scrollToPosition(pendingScrollPosition)
      }
    } else {
      afterScroll?.invoke()
      afterScroll = null
    }
  }

  companion object {
    private val TAG = Log.tag(ConversationLayoutManager::class.java)
  }
}
