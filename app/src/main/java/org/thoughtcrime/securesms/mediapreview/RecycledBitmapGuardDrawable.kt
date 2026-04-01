package org.thoughtcrime.securesms.mediapreview

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

/**
 * A wrapper that skips drawing upon failure. This is to guard against situations where we may
 * be using a bitmap from Glide that could be recycled at a time outside our control
 *
 * If you ever truly need the bitmap in this case, you should save it yourself. But there are situations
 * (like transition animations) where having a bitmap isn't strictly necessary, and we'd rather
 * show nothing than crash or have to manage the bitmap lifecycle ourselves.
 */
class RecycledBitmapGuardDrawable(drawable: Drawable) : DrawableWrapper(drawable) {
  override fun draw(canvas: Canvas) {
    try {
      super.draw(canvas)
    } catch (_: RuntimeException) {
      // Bitmap was recycled — nothing to draw.
    }
  }
}
