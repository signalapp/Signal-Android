package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
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

  private val bounds = Rect()
  private val boundsF = RectF()
  private val path = Path()

  override fun draw(canvas: Canvas) {
    canvas.getClipBounds(bounds)
    boundsF.set(bounds)
    path.reset()

    path.addRoundRect(boundsF, radius, radius, Path.Direction.CW)
    canvas.withClip(path) {
      super.draw(canvas)
    }
  }
}
