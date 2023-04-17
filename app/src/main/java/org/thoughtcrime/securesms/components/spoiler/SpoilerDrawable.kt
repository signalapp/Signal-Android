package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * Drawable that animates a sparkle effect for spoilers.
 */
class SpoilerDrawable(@ColorInt color: Int) : Drawable() {

  private val paint = Paint()

  init {
    alpha = 255
    setTintColor(color)
  }

  fun setTintColor(@ColorInt color: Int) {
    paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
  }

  override fun draw(canvas: Canvas) {
    paint.shader = SpoilerPaint.shader
    canvas.drawRect(bounds, paint)
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
  }

  @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
  override fun getOpacity(): Int {
    return PixelFormat.TRANSPARENT
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    throw UnsupportedOperationException("Call setTintColor")
  }
}
