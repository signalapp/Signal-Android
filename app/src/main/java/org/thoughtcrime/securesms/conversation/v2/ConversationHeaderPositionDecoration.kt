/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationHeaderView
import kotlin.math.min

/**
 * Reserves space above the [ConversationHeaderView] for the toolbar and adjusts the conversation RecyclerView's translationY so the header is pinned below the
 * toolbar when content is short enough to fit the viewport. The toolbar margin is only contributed when a translation is actually going to be applied; when
 * content overflows, no margin is added and no translation is applied.
 *
 * Whether content overflows is read at draw time, once layout has finished. Reading it mid-layout sees a partial set of
 * children, which made the margin flip on and off between layouts.
 */
class ConversationHeaderPositionDecoration : RecyclerView.ItemDecoration() {
  private val bounds = Rect()

  var toolbarMargin: Int = 0

  private var canScrollDownAtLastDraw: Boolean? = null
  private var isHeaderMarginApplied: Boolean? = null

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    super.getItemOffsets(outRect, view, parent, state)
    if (view is ConversationHeaderView) {
      val isMarginApplied = !(canScrollDownAtLastDraw ?: parent.canScrollVertically(1))
      isHeaderMarginApplied = isMarginApplied
      if (isMarginApplied) {
        outRect.top = toolbarMargin
      }
    }
  }

  /**
   * Whether the header's margin was decided from a scroll extent that no longer holds, and so needs another layout to be
   * placed correctly. This is the case after the first layout, which has no previous draw to go by.
   */
  fun isHeaderMarginStale(parent: RecyclerView): Boolean {
    val isMarginApplied = isHeaderMarginApplied ?: return false
    return parent.children.any { it is ConversationHeaderView } && isMarginApplied == parent.canScrollVertically(1)
  }

  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val canScrollDown = parent.canScrollVertically(1)
    canScrollDownAtLastDraw = canScrollDown

    if (canScrollDown) {
      parent.translationY = 0f
      return
    }

    val threadHeaderView: ConversationHeaderView = parent.children
      .filterIsInstance<ConversationHeaderView>()
      .firstOrNull() ?: run {
      parent.translationY = 0f
      return
    }

    parent.getDecoratedBoundsWithMargins(threadHeaderView, bounds)
    val margin = bounds.bottom - bounds.top - threadHeaderView.height
    val childTop: Int = threadHeaderView.top - margin
    parent.translationY = min(0, -childTop).toFloat()
  }
}
