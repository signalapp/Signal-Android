/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat

/**
 * Custom LinearLayoutCompat that will intercept EXACTLY measure-specs and
 * overwrite them with AT_MOST. This guarantees that wrap_content is respected
 * when the Layout is within a constraintlayout.
 */
class ShrinkWrapLinearLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : LinearLayoutCompat(context, attrs) {
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val shrinkWrapWidthSpec = shrinkWrapWidthMeasureSpec(widthMeasureSpec)
    super.onMeasure(shrinkWrapWidthSpec, heightMeasureSpec)
  }

  private fun shrinkWrapWidthMeasureSpec(widthMeasureSpec: Int): Int {
    val mode = MeasureSpec.getMode(widthMeasureSpec)
    val size = MeasureSpec.getSize(widthMeasureSpec)

    return if (mode == MeasureSpec.EXACTLY) {
      MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST)
    } else {
      widthMeasureSpec
    }
  }
}
