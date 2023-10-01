package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.core.graphics.withClip
import com.google.android.material.card.MaterialCardView

/**
 * Adds manual clipping around the card. This ensures that software rendering
 * still maintains border radius of cards.
 */
class ClippedCardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

  private val boundsF = RectF()
  private val path = Path()

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    boundsF.set(0f, 0f, w.toFloat(), h.toFloat())
  }

  override fun draw(canvas: Canvas) {
    path.reset()
    path.addRoundRect(boundsF, radius, radius, Path.Direction.CW)
    canvas.withClip(path) {
      super.draw(canvas)
    }
  }
}
