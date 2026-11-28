/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.view.children
import androidx.media3.common.MediaItem

/**
 * The spot in a cell where an [InlineVideoSlot]'s texture view goes. Attaching and detaching skip requestLayout so moving
 * a slot mid-scroll doesn't relayout the list.
 */
class InlineVideoHost @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

  var slot: InlineVideoSlot? = null
    private set

  private var cornerRadius = 0f

  init {
    outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
      }
    }
  }

  fun setCornerRadius(radius: Float) {
    cornerRadius = radius
    clipToOutline = radius > 0f
    invalidateOutline()
  }

  fun isShowing(mediaItem: MediaItem?): Boolean {
    val slot = slot ?: return false
    return mediaItem != null && slot.mediaItem == mediaItem && slot.hasRenderedFirstFrame
  }

  fun attach(slot: InlineVideoSlot) {
    check(this.slot == null) { "Host already has a slot." }

    val view = slot.textureView
    check(view.parent == null) { "Slot is still hosted elsewhere." }

    addViewInLayout(view, -1, view.layoutParams ?: generateDefaultLayoutParams(), true)
    fill(view)
    invalidate()

    this.slot = slot
  }

  fun detach(): InlineVideoSlot? {
    val slot = this.slot ?: return null

    removeViewInLayout(slot.textureView)
    invalidate()

    this.slot = null
    return slot
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(exactSizeOrZero(widthMeasureSpec), exactSizeOrZero(heightMeasureSpec))
  }

  private fun exactSizeOrZero(measureSpec: Int): Int {
    return if (MeasureSpec.getMode(measureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(measureSpec) else 0
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    children.forEach { fill(it) }
  }

  override fun generateDefaultLayoutParams(): LayoutParams {
    return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  private fun fill(child: View) {
    child.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    )
    child.layout(0, 0, width, height)
  }
}
