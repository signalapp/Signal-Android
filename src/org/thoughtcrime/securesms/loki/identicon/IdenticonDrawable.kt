package org.thoughtcrime.securesms.loki.identicon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * Basically a [Bitmap] wrapper, the [Bitmap] size must be known when instantiating it
 * but when drawing it will draw the [Bitmap] to fit the canvas.
 */
abstract class IdenticonDrawable(
        width: Int,
        height: Int,
        hash: Long
) : Drawable() {

  private val bitmapRect: Rect = Rect(0, 0, width, height)
  private val destinationRect: Rect = Rect(0, 0, width, height)
  private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  private val canvas: Canvas = Canvas(bitmap)
  private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  var hash: Long = hash
    set(value) {
      field = value
      onSetHash(value)
      invalidateBitmap()
    }

  protected fun invalidateBitmap() {
    drawBitmap(canvas)
    invalidateSelf()
  }

  protected abstract fun drawBitmap(canvas: Canvas)

  protected open fun onSetHash(newHash: Long) = Unit

  override fun draw(canvas: Canvas) {
    destinationRect.set(0, 0, canvas.width, canvas.height)
    canvas.drawBitmap(bitmap, bitmapRect, destinationRect, bitmapPaint)
  }

  override fun setAlpha(i: Int) {
    bitmapPaint.alpha = i
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    bitmapPaint.colorFilter = colorFilter
  }

  override fun getOpacity(): Int {
    return bitmapPaint.alpha
  }
}