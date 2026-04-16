/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationHeaderView
import kotlin.math.min

/**
 * Adjusts the Conversation's recycler view translationY so that the conversation header
 * is pinned to the top of the visible area when content is too short to
 * fill the screen.
 */
class ConversationHeaderPositionDecoration : RecyclerView.ItemDecoration() {
  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    if (parent.childCount == 0 || parent.canScrollVertically(-1) || parent.canScrollVertically(1)) {
      parent.translationY = 0f
    } else {
      val threadHeaderView: ConversationHeaderView = parent.children
        .filterIsInstance<ConversationHeaderView>()
        .firstOrNull() ?: run {
        parent.translationY = 0f
        return
      }

      // A decorator adds the margin for the toolbar, margin is the difference of the bounds "height" and the view height
      val bounds = Rect()
      parent.getDecoratedBoundsWithMargins(threadHeaderView, bounds)
      val toolbarMargin = bounds.bottom - bounds.top - threadHeaderView.height

      val childTop: Int = threadHeaderView.top - toolbarMargin
      parent.translationY = min(0, -childTop).toFloat()
    }
  }
}
