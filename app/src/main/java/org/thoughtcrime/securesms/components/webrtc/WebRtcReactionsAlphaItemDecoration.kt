/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration

/**
 * This fades the top 2 reactions slightly inside their recyclerview.
 */
class WebRtcReactionsAlphaItemDecoration : ItemDecoration() {
  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    for (i in 0..parent.childCount) {
      val child = parent.getChildAt(i) ?: continue
      when (parent.getChildAdapterPosition(child)) {
        WebRtcReactionsRecyclerAdapter.MAX_REACTION_NUMBER - 1 -> child.alpha = 0.7f
        WebRtcReactionsRecyclerAdapter.MAX_REACTION_NUMBER - 2 -> child.alpha = 0.9f
        else -> child.alpha = 1f
      }
    }
  }
}
