package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.ColorInt
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.components.emoji.EmojiTextView

class StoryTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : EmojiTextView(context, attrs) {

  private val canvasBounds: Rect = Rect()
  private val textBounds: RectF = RectF()
  private val wrappedBackgroundPaint = Paint().apply {
    style = Paint.Style.FILL
    isAntiAlias = true
    color = Color.TRANSPARENT
  }

  init {
    if (isInEditMode) {
      wrappedBackgroundPaint.color = Color.RED
    }
  }

  fun setWrappedBackgroundColor(@ColorInt color: Int) {
    wrappedBackgroundPaint.color = color
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    if (layout == null) {
      invalidate()
    }

    if (wrappedBackgroundPaint.color != Color.TRANSPARENT && layout != null) {
      canvas.getClipBounds(canvasBounds)
      textBounds.set(canvasBounds)

      val maxWidth = (0 until layout.lineCount).map {
        layout.getLineWidth(it)
      }.maxOrNull()

      if (maxWidth != null) {
        textBounds.inset((width - maxWidth - paddingStart - paddingEnd) / 2f, 0f)

        canvas.drawRoundRect(
          textBounds,
          DimensionUnit.DP.toPixels(18f),
          DimensionUnit.DP.toPixels(18f),
          wrappedBackgroundPaint
        )
      }
    }

    super.onDraw(canvas)
  }
}
