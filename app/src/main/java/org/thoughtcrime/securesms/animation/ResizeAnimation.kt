/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.animation

import android.graphics.Point
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation

class ResizeAnimation : Animation {
  private val target: View
  private val targetWidthPx: Int
  private val targetHeightPx: Int

  private var startWidth: Int = 0
  private var startHeight: Int = 0

  constructor(target: View, dimension: Point) : this(target, dimension.x, dimension.y)

  constructor(target: View, targetWidthPx: Int, targetHeightPx: Int) {
    this.target = target
    this.targetWidthPx = targetWidthPx
    this.targetHeightPx = targetHeightPx
  }

  override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
    val newWidth = (startWidth + (targetWidthPx - startWidth) * interpolatedTime).toInt()
    val newHeight = (startHeight + (targetHeightPx - startHeight) * interpolatedTime).toInt()

    target.layoutParams.apply {
      width = newWidth
      height = newHeight
    }
  }

  override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
    super.initialize(width, height, parentWidth, parentHeight)

    startWidth = width
    startHeight = height
  }

  override fun willChangeBounds(): Boolean = true
}
