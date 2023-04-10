package org.thoughtcrime.securesms.components.spoiler

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * Drawable that animates a sparkle effect for spoilers.
 */
class SpoilerDrawable(@ColorInt color: Int) : Drawable() {

  init {
    alpha = 255
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
  }

  override fun draw(canvas: Canvas) {
    canvas.drawRect(bounds, SpoilerPaint.paint)
    SpoilerPaint.update()
    invalidateSelf()
  }

  override fun setAlpha(alpha: Int) {
    SpoilerPaint.applyAlpha(alpha)
  }

  @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
  override fun getOpacity(): Int {
    return SpoilerPaint.paint.alpha
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    SpoilerPaint.applyColorFilter(colorFilter)
  }
}
