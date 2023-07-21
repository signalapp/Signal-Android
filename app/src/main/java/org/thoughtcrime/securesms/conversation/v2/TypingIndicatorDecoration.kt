/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ConversationTypingView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Displays a typing indicator as a part of the very last (first) item in the adapter.
 */
class TypingIndicatorDecoration(
  private val context: Context,
  private val rootView: RecyclerView
) : ItemDecoration() {

  companion object {
    private val TAG = Log.tag(TypingIndicatorDecoration::class.java)
  }

  private val typingView: ConversationTypingView by lazy(LazyThreadSafetyMode.NONE) {
    LayoutInflater.from(context).inflate(R.layout.conversation_typing_view, rootView, false) as ConversationTypingView
  }

  private var displayIndicator = false
  private var animationFraction = 0f
  private var offsetAnimator: ValueAnimator? = null

  init {
    rootView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
      if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
        remeasureTypingView()
        rootView.invalidateItemDecorations()
      }
    }
  }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    if (!displayIndicator && animationFraction == 0f) {
      return outRect.set(0, 0, 0, 0)
    }

    if (parent.getChildAdapterPosition(view) == 0) {
      remeasureTypingView()
      outRect.set(0, 0, 0, (typingView.measuredHeight * animationFraction).toInt())
    } else {
      outRect.set(0, 0, 0, 0)
    }
  }

  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    if (!displayIndicator && offsetAnimator?.isRunning != true) {
      return
    }

    val firstChild = parent.children.firstOrNull() ?: return

    if (parent.getChildAdapterPosition(firstChild) == 0) {
      c.withTranslation(
        x = firstChild.left.toFloat(),
        y = firstChild.bottom.toFloat()
      ) {
        typingView.draw(this)
      }

      if (typingView.isActive) {
        rootView.post { rootView.invalidateItemDecorations() }
      }
    }
  }

  fun setTypists(
    glideRequests: GlideRequests,
    typists: List<Recipient>,
    isGroupThread: Boolean,
    hasWallpaper: Boolean,
    isReplacedByIncomingMessage: Boolean
  ) {
    Log.d(TAG, "setTypists: Updating typists: ${typists.size} $isGroupThread $hasWallpaper $isReplacedByIncomingMessage")

    val isEdge = displayIndicator != typists.isNotEmpty()
    displayIndicator = typists.isNotEmpty()

    typingView.setTypists(
      glideRequests,
      typists,
      isGroupThread,
      hasWallpaper
    )
    remeasureTypingView()
    rootView.invalidateItemDecorations()

    if (isReplacedByIncomingMessage) {
      offsetAnimator?.cancel()
      animationFraction = 0f
    } else if (isEdge) {
      animateOffset()
    }
  }

  private fun animateOffset() {
    offsetAnimator?.cancel()

    val (start, end) = if (displayIndicator) {
      animationFraction to 1f
    } else {
      animationFraction to 0f
    }

    offsetAnimator = ValueAnimator.ofFloat(start, end).apply {
      addUpdateListener {
        animationFraction = it.animatedValue as Float
        rootView.invalidateItemDecorations()
      }
      start()
    }
  }

  private fun remeasureTypingView() {
    with(typingView) {
      measure(
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
      )
      layout(
        0,
        0,
        typingView.measuredWidth,
        typingView.measuredHeight
      )
    }
  }
}
