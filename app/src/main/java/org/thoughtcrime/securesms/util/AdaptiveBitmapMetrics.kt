/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.signal.core.util.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Describes the absolute measurements for adaptive icon bitmaps.
 *
 * Because adaptive icons are meant to be 108dp with a 72dp image,
 * this can result in subpixel measurement on some devices. This class
 * is responsible for on-the-fly calculation of metrics that'll look good
 * and not cause off-by-a-pixel errors in Adaptive bitmaps.
 */
object AdaptiveBitmapMetrics {
  private val ADAPTIVE_ICON_OUTER_SIZE: Float = 108f.dp
  private val ADAPTIVE_ICON_INNER_SIZE: Float = 72f.dp

  @get:JvmStatic
  val outerWidth = ceil(ADAPTIVE_ICON_OUTER_SIZE).toInt()

  @get:JvmStatic
  val innerWidth = ceil(ADAPTIVE_ICON_INNER_SIZE).let { ceiling ->
    if (floor(ADAPTIVE_ICON_OUTER_SIZE) < outerWidth) {
      ceiling + 2
    } else {
      ceiling
    }.toInt()
  }

  @get:JvmStatic
  val padding = (outerWidth - innerWidth) / 2f
}
