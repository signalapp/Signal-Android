package org.thoughtcrime.securesms.mediapreview

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A wrapper that skips drawing upon failure. This is to guard against situations where we may
 * be using a bitmap from Glide that could be recycled at a time outside our control
 *
 * If you ever truly need the bitmap in this case, you should save it yourself. But there are situations
 * (like transition animations) where having a bitmap isn't strictly necessary, and we'd rather
 * show nothing than crash or have to manage the bitmap lifecycle ourselves.
 */
class RecycledBitmapGuardDrawable(private val inner: Drawable) : Drawable() {

  init {
    val b = inner.bounds
    setBounds(b.left, b.top, b.right, b.bottom)
  }

  override fun draw(canvas: Canvas) {
    val savedBounds = inner.copyBounds()
    inner.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    try {
      inner.draw(canvas)
    } catch (_: RuntimeException) {
      // Bitmap was recycled — nothing to draw.
    } finally {
      inner.setBounds(savedBounds.left, savedBounds.top, savedBounds.right, savedBounds.bottom)
    }
  }

  override fun getIntrinsicWidth(): Int {
    return inner.intrinsicWidth
  }

  override fun getIntrinsicHeight(): Int {
    return inner.intrinsicHeight
  }

  override fun setAlpha(alpha: Int) {
    inner.alpha = alpha
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    inner.colorFilter = colorFilter
  }

  @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }
}
