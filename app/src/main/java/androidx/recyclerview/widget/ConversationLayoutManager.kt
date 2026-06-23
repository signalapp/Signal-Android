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

  // Backing state for scrollToPositionTopAligned; alignTopCorrected guards the one-shot corrective re-scroll.
  private var alignTopPosition: Int = RecyclerView.NO_POSITION
  private var alignTopInset: Int = 0
  private var alignTopCorrected: Boolean = false

  override fun supportsPredictiveItemAnimations(): Boolean {
    return false
  }

  /**
   * Scroll to the desired position and be notified when the layout manager has completed the request
   * via [afterScroll] callback.
   */
  fun scrollToPositionWithOffset(position: Int, offset: Int, afterScroll: () -> Unit) {
    this.afterScroll = afterScroll
    alignTopPosition = RecyclerView.NO_POSITION
    super.scrollToPositionWithOffset(position, offset)
  }

  /**
   * Scroll so [position]'s decorated top (including any top decoration, e.g. the unread divider) lands [topInset] px
   * below the top of the recycler. [afterScroll] fires once the alignment settles.
   */
  fun scrollToPositionTopAligned(position: Int, topInset: Int, afterScroll: () -> Unit) {
    this.afterScroll = afterScroll
    alignTopPosition = position
    alignTopInset = topInset
    alignTopCorrected = false
    // Rough first pass: the exact offset needs the item's height, which isn't known until it's laid out (see onLayoutCompleted).
    super.scrollToPositionWithOffset(position, height - topInset)
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
      return
    }

    // The target is now laid out, so its height is known. Correct the offset once so the decorated top sits at the
    // requested inset, then let the next layout settle before notifying via afterScroll.
    if (alignTopPosition != RecyclerView.NO_POSITION && !alignTopCorrected) {
      val target = findViewByPosition(alignTopPosition)
      if (target != null) {
        alignTopCorrected = true
        if (getDecoratedTop(target) != alignTopInset) {
          val correctedOffset = (height - paddingBottom) - alignTopInset - getDecoratedMeasuredHeight(target)
          super.scrollToPositionWithOffset(alignTopPosition, correctedOffset)
          return
        }
      }
    }

    afterScroll?.invoke()
    afterScroll = null
    alignTopPosition = RecyclerView.NO_POSITION
  }

  companion object {
    private val TAG = Log.tag(ConversationLayoutManager::class.java)
  }
}
