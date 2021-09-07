package org.thoughtcrime.securesms.imageeditor.renderers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.thoughtcrime.securesms.imageeditor.Bounds
import org.thoughtcrime.securesms.imageeditor.RendererContext
import org.thoughtcrime.securesms.util.ViewUtil

class SelectedElementGuideRenderer {

  companion object {
    private const val PADDING: Int = 10
  }

  private val allPointsOnScreen = FloatArray(8)
  private val allPointsInLocalCords = floatArrayOf(
    Bounds.LEFT, Bounds.TOP,
    Bounds.RIGHT, Bounds.TOP,
    Bounds.RIGHT, Bounds.BOTTOM,
    Bounds.LEFT, Bounds.BOTTOM
  )

  private val circleRadius = ViewUtil.dpToPx(5).toFloat()

  private val guidePaint = Paint().apply {
    isAntiAlias = true
    strokeWidth = ViewUtil.dpToPx(15).toFloat() / 10f
    color = Color.WHITE
    style = Paint.Style.STROKE
  }

  private val circlePaint = Paint().apply {
    isAntiAlias = true
    color = Color.WHITE
    style = Paint.Style.FILL
  }

  private val path = Path()

  /**
   * Draw self to the context.
   *
   * @param rendererContext The context to draw to.
   */
  fun render(rendererContext: RendererContext) {
    rendererContext.canvasMatrix.mapPoints(allPointsOnScreen, allPointsInLocalCords)
    performRender(rendererContext)
  }

  fun render(rendererContext: RendererContext, contentBounds: RectF) {
    rendererContext.canvasMatrix.mapPoints(
      allPointsOnScreen,
      floatArrayOf(
        contentBounds.left - PADDING,
        contentBounds.top - PADDING,
        contentBounds.right + PADDING,
        contentBounds.top - PADDING,
        contentBounds.right + PADDING,
        contentBounds.bottom + PADDING,
        contentBounds.left - PADDING,
        contentBounds.bottom + PADDING
      )
    )

    performRender(rendererContext)
  }

  private fun performRender(rendererContext: RendererContext) {
    rendererContext.save()

    rendererContext.canvasMatrix.setToIdentity()

    path.reset()
    path.moveTo(allPointsOnScreen[0], allPointsOnScreen[1])
    path.lineTo(allPointsOnScreen[2], allPointsOnScreen[3])
    path.lineTo(allPointsOnScreen[4], allPointsOnScreen[5])
    path.lineTo(allPointsOnScreen[6], allPointsOnScreen[7])
    path.close()

    rendererContext.canvas.drawPath(path, guidePaint)
    // TODO: Implement scaling
//    rendererContext.canvas.drawCircle(
//      (allPointsOnScreen[6] + allPointsOnScreen[0]) / 2f,
//      (allPointsOnScreen[7] + allPointsOnScreen[1]) / 2f,
//      circleRadius,
//      circlePaint
//    )
//    rendererContext.canvas.drawCircle(
//      (allPointsOnScreen[4] + allPointsOnScreen[2]) / 2f,
//      (allPointsOnScreen[5] + allPointsOnScreen[3]) / 2f,
//      circleRadius,
//      circlePaint
//    )

    rendererContext.restore()
  }
}
