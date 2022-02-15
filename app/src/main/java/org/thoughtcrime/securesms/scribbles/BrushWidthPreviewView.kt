package org.thoughtcrime.securesms.scribbles

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import org.signal.imageeditor.core.Bounds
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

class BrushWidthPreviewView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val tmpRect = Rect()

  private var animator: Animator? = null

  private val backdropPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.WHITE
  }

  private val brushPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
    color = Color.WHITE
  }

  private var radius: Float = (0.03f * Bounds.FULL_BOUNDS.width() / 2f)
  private var isBlur: Boolean = false

  fun setBlur(blur: Boolean) {
    isBlur = blur
    invalidate()
  }

  fun setColor(@ColorInt color: Int) {
    brushPaint.color = color
    invalidate()
  }

  fun setThickness(thickness: Float) {
    radius = (thickness * Bounds.FULL_BOUNDS.width()) / 2f
    invalidate()
  }

  fun show() {
    visible = true
    animator?.cancel()
    animator = ObjectAnimator.ofFloat(this, "alpha", 1f).apply {
      interpolator = MediaAnimations.interpolator
      duration = 150L
      start()
    }
  }

  fun hide() {
    animator?.cancel()
    animator = ObjectAnimator.ofFloat(this, "alpha", 0f).apply {
      interpolator = MediaAnimations.interpolator
      duration = 150L
      doOnEnd { visible = false }
      start()
    }
  }

  override fun onDraw(canvas: Canvas) {
    canvas.getClipBounds(tmpRect)
    canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), radius + ViewUtil.dpToPx(1), backdropPaint)

    if (!isBlur) {
      canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), radius, brushPaint)
    }
  }
}
