/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow

/**
 * Helper class for screenshotting compose views.
 *
 * You need to call bind from the compose, passing in the
 * LocalView.current view with bounds fetched from when the
 * composable is globally positioned.
 *
 * See QrCodeBadge.kt for an example
 */
class ScreenshotController {
  private var screenshotCallback: (() -> Bitmap?)? = null

  fun bind(view: View, bounds: Rect?) {
    if (bounds == null) {
      screenshotCallback = null
      return
    }
    screenshotCallback = {
      val bitmap = Bitmap.createBitmap(
        bounds.width.toInt(),
        bounds.height.toInt(),
        Bitmap.Config.ARGB_8888
      )

      if (Build.VERSION.SDK_INT >= 26) {
        PixelCopy.request(
          (view.context as Activity).window,
          android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
          bitmap,
          {},
          Handler(Looper.getMainLooper())
        )
      } else {
        val canvas = Canvas(bitmap)
          .apply {
            translate(-bounds.left, -bounds.top)
          }
        view.draw(canvas)
      }

      bitmap
    }
  }

  fun screenshot(): Bitmap? {
    return screenshotCallback?.invoke()
  }
}

fun LayoutCoordinates.getScreenshotBounds(): Rect {
  return if (Build.VERSION.SDK_INT >= 26) {
    this.boundsInWindow()
  } else {
    this.boundsInRoot()
  }
}
