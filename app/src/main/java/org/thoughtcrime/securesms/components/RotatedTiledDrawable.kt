package org.thoughtcrime.securesms.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Draws [bitmap] as a repeating tiled pattern rotated by [rotationDegrees].
 */
class RotatedTiledDrawable(
  private val bitmap: Bitmap,
  private val rotationDegrees: Float
) : Drawable() {

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = android.graphics.BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
  }

  override fun onBoundsChange(bounds: android.graphics.Rect) {
    paint.shader.setLocalMatrix(
      Matrix().apply { setRotate(rotationDegrees, bounds.exactCenterX(), bounds.exactCenterY()) }
    )
  }

  override fun draw(canvas: Canvas) {
    canvas.drawRect(bounds, paint)
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
    invalidateSelf()
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    paint.colorFilter = colorFilter
    invalidateSelf()
  }

  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
