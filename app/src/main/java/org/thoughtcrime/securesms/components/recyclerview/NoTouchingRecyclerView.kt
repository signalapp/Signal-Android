/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.recyclerview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * Ignores all touch events, purely for rendering views in a recyclable manner.
 */
class NoTouchingRecyclerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(e: MotionEvent?): Boolean {
    return false
  }

  override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
    return false
  }
}
