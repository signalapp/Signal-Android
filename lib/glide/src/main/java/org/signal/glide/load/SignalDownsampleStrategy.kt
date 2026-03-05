/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.glide.load

import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlin.math.max
import kotlin.math.min

object SignalDownsampleStrategy {
  /**
   * Center outside, but don't up-scale, only downscale. You should be setting centerOutside
   * on the target image view to still maintain center outside behavior.
   */
  @JvmField
  val CENTER_OUTSIDE_NO_UPSCALE: DownsampleStrategy = CenterOutsideNoUpscale()

  private class CenterOutsideNoUpscale : DownsampleStrategy() {
    override fun getScaleFactor(
      sourceWidth: Int,
      sourceHeight: Int,
      requestedWidth: Int,
      requestedHeight: Int
    ): Float {
      val widthPercentage = requestedWidth / sourceWidth.toFloat()
      val heightPercentage = requestedHeight / sourceHeight.toFloat()
      return min(MAX_SCALE_FACTOR, max(widthPercentage, heightPercentage))
    }

    override fun getSampleSizeRounding(
      sourceWidth: Int,
      sourceHeight: Int,
      requestedWidth: Int,
      requestedHeight: Int
    ): SampleSizeRounding {
      return SampleSizeRounding.QUALITY
    }

    companion object {
      private const val MAX_SCALE_FACTOR = 1f
    }
  }
}
