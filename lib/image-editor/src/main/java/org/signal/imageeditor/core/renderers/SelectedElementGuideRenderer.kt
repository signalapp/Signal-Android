package org.signal.imageeditor.core.renderers

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Parcel
import android.os.Parcelable
import org.signal.core.util.DimensionUnit
import org.signal.imageeditor.core.Bounds
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext

class SelectedElementGuideRenderer : Renderer {

  private val allPointsOnScreen = FloatArray(8)
  private val allPointsInLocalCords = floatArrayOf(
    Bounds.LEFT,
    Bounds.TOP,
    Bounds.RIGHT,
    Bounds.TOP,
    Bounds.RIGHT,
    Bounds.BOTTOM,
    Bounds.LEFT,
    Bounds.BOTTOM
  )

  private val circleRadius = DimensionUnit.DP.toPixels(5f)

  private val guidePaint = Paint().apply {
    isAntiAlias = true
    strokeWidth = DimensionUnit.DP.toPixels(1.5f)
    color = Color.WHITE
    style = Paint.Style.STROKE
    pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
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
  override fun render(rendererContext: RendererContext) {
    rendererContext.canvasMatrix.mapPoints(allPointsOnScreen, allPointsInLocalCords)
    performRender(rendererContext)
  }

  override fun hitTest(x: Float, y: Float): Boolean = false

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
    rendererContext.canvas.drawCircle(
      (allPointsOnScreen[6] + allPointsOnScreen[0]) / 2f,
      (allPointsOnScreen[7] + allPointsOnScreen[1]) / 2f,
      circleRadius,
      circlePaint
    )
    rendererContext.canvas.drawCircle(
      (allPointsOnScreen[4] + allPointsOnScreen[2]) / 2f,
      (allPointsOnScreen[5] + allPointsOnScreen[3]) / 2f,
      circleRadius,
      circlePaint
    )

    rendererContext.restore()
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<SelectedElementGuideRenderer> {
    override fun createFromParcel(parcel: Parcel): SelectedElementGuideRenderer {
      return SelectedElementGuideRenderer()
    }

    override fun newArray(size: Int): Array<SelectedElementGuideRenderer?> {
      return arrayOfNulls(size)
    }
  }
}
