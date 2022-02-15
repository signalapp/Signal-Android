package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.thoughtcrime.securesms.util.Projection

/**
 * ColorizerView takes a list of projections and uses them to create a mask over it's background.
 */
class ColorizerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val clipPath = Path()
  private var projections: List<Projection> = listOf()

  fun setProjections(projections: List<Projection>) {
    this.projections = projections
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    if (projections.isNotEmpty()) {
      canvas.save()
      clipPath.rewind()

      projections.forEach {
        it.applyToPath(clipPath)
      }

      canvas.clipPath(clipPath)
      super.draw(canvas)
      canvas.restore()
    } else {
      super.draw(canvas)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    projections.forEach { it.release() }
    projections = emptyList()
  }
}
