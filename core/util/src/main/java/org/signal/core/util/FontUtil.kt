package org.signal.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import kotlin.math.abs

object FontUtil {
  private const val SAMPLE_EMOJI = "\uD83C\uDF0D" // 🌍

  /**
   * Certain platforms cannot render emoji above a certain font size.
   *
   * This will attempt to render an emoji at the specified font size and tell you if it's possible.
   * It does this by rendering an emoji into a 1x1 bitmap and seeing if the resulting pixel is non-transparent.
   *
   * https://stackoverflow.com/a/50988748
   */
  @JvmStatic
  fun canRenderEmojiAtFontSize(size: Float): Boolean {
    val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    paint.textSize = size
    paint.textAlign = Paint.Align.CENTER

    val ascent: Float = abs(paint.ascent())
    val descent: Float = abs(paint.descent())
    val halfHeight = (ascent + descent) / 2.0f

    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    canvas.drawText(SAMPLE_EMOJI, 0.5f, 0.5f + halfHeight - descent, paint)

    return bitmap.getPixel(0, 0) != 0
  }
}
