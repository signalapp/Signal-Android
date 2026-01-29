/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * Creates a scaled bitmap with the given maximum dimensions while maintaining the original aspect ratio.
 */
fun Bitmap.scaleWithAspectRatio(maxWidth: Int, maxHeight: Int): Bitmap {
  if (getWidth() <= maxWidth && getHeight() <= maxHeight) {
    return this
  }

  if (maxWidth <= 0 || maxHeight <= 0) {
    return this
  }

  var newWidth = maxWidth
  var newHeight = maxHeight

  val widthRatio: Float = getWidth() / maxWidth.toFloat()
  val heightRatio: Float = getHeight() / maxHeight.toFloat()

  if (widthRatio > heightRatio) {
    newHeight = (getHeight() / widthRatio).toInt()
  } else {
    newWidth = (getWidth() / heightRatio).toInt()
  }

  return scale(newWidth, newHeight)
}