package org.thoughtcrime.securesms.conversation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Region
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import org.thoughtcrime.securesms.util.Projection

/**
 * Drawable which clips out the given projection
 */
class ClipProjectionDrawable(wrapped: Drawable) : LayerDrawable(arrayOf(wrapped)) {

  constructor() : this(ColorDrawable(Color.TRANSPARENT))

  init {
    setId(0, 0)
  }

  private val clipPath = Path()
  private var projections: List<Projection> = listOf()

  fun setWrappedDrawable(drawable: Drawable) {
    setDrawableByLayerId(0, drawable)
  }

  fun setProjections(projections: Set<Projection>) {
    this.projections = projections.toList()
    invalidateSelf()
  }

  fun clearProjections() {
    this.projections = listOf()
    invalidateSelf()
  }

  override fun draw(canvas: Canvas) {
    if (projections.isNotEmpty()) {
      canvas.save()
      clipPath.rewind()

      projections.forEach {
        it.applyToPath(clipPath)
      }

      canvas.clipPath(clipPath, Region.Op.DIFFERENCE)
      super.draw(canvas)
      canvas.restore()
    } else {
      super.draw(canvas)
    }
  }
}
