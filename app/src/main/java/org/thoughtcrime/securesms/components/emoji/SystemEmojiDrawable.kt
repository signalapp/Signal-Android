package org.thoughtcrime.securesms.components.emoji

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.toRectF
import androidx.core.graphics.withMatrix
import androidx.emoji2.text.EmojiCompat

/**
 * [Drawable] that renders an emoji via the system font for available glyphs and EmojiCompat for
 * missing glyphs.
 */
class SystemEmojiDrawable(emoji: CharSequence) : Drawable() {

  private val emojiLayout: StaticLayout = getStaticLayout(getProcessedEmoji(emoji))
  private val transform: Matrix = Matrix()

  override fun onBoundsChange(bounds: Rect) {
    super.onBoundsChange(bounds)
    transform.setRectToRect(emojiLayout.getBounds(), bounds.toRectF(), Matrix.ScaleToFit.CENTER)
  }

  override fun draw(canvas: Canvas) {
    canvas.withMatrix(transform) {
      emojiLayout.draw(canvas)
    }
  }

  override fun setAlpha(alpha: Int) {}

  override fun setColorFilter(colorFilter: ColorFilter?) {}

  @Deprecated(
    "Deprecated in Java",
    ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
  )
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

  companion object {
    private val textPaint: TextPaint = TextPaint()

    private fun getStaticLayout(emoji: CharSequence): StaticLayout =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(emoji, 0, emoji.length, textPaint, Int.MAX_VALUE).build()
      } else {
        @Suppress("DEPRECATION")
        StaticLayout(emoji, textPaint, Int.MAX_VALUE, Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true)
      }

    private fun getProcessedEmoji(emoji: CharSequence): CharSequence =
      try {
        EmojiCompat.get().process(emoji) ?: emoji
      } catch (e: IllegalStateException) {
        emoji
      }

    private fun StaticLayout.getBounds(): RectF =
      RectF(getLineLeft(0), 0f, getLineRight(0), getLineDescent(0) - getLineAscent(0).toFloat())
  }
}
