package org.signal.imageeditor.core.renderers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.view.animation.Interpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.signal.core.util.DimensionUnit
import org.signal.imageeditor.R
import org.signal.imageeditor.core.Bounds
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext

internal class TrashRenderer : InvalidateableRenderer, Renderer, Parcelable {

  private val outlinePaint = Paint().apply {
    isAntiAlias = true
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = DimensionUnit.DP.toPixels(1.5f)
  }

  private val shadePaint = Paint().apply {
    isAntiAlias = true
    color = 0x99000000.toInt()
    style = Paint.Style.FILL
  }

  private val bounds = RectF()

  private val diameterSmall = DimensionUnit.DP.toPixels(41f)
  private val diameterLarge = DimensionUnit.DP.toPixels(54f)
  private val trashSize: Int = DimensionUnit.DP.toPixels(24f).toInt()
  private val padBottom = DimensionUnit.DP.toPixels(16f)
  private val interpolator: Interpolator = FastOutSlowInInterpolator()

  private var startTime = 0L

  private var isExpanding = false

  private val buttonCenter = FloatArray(2)

  constructor()

  override fun render(rendererContext: RendererContext) {
    super.render(rendererContext)

    val frameRenderTime = System.currentTimeMillis()

    val trash: Drawable = requireNotNull(AppCompatResources.getDrawable(rendererContext.context, R.drawable.ic_trash_white_24))
    trash.setBounds(0, 0, trashSize, trashSize)

    val diameter = getInterpolatedDiameter(frameRenderTime - startTime)

    rendererContext.canvas.save()
    rendererContext.mapRect(bounds, Bounds.FULL_BOUNDS)

    buttonCenter[0] = bounds.centerX()
    buttonCenter[1] = bounds.bottom - diameterLarge / 2f - padBottom

    rendererContext.canvasMatrix.setToIdentity()

    rendererContext.canvas.drawCircle(buttonCenter[0], buttonCenter[1], diameter / 2f, shadePaint)
    rendererContext.canvas.drawCircle(buttonCenter[0], buttonCenter[1], diameter / 2f, outlinePaint)
    rendererContext.canvas.translate(bounds.centerX(), bounds.bottom - diameterLarge / 2f - padBottom)
    rendererContext.canvas.translate(-(trashSize / 2f), -(trashSize / 2f))
    trash.draw(rendererContext.canvas)
    rendererContext.canvas.restore()

    if (frameRenderTime - DURATION < startTime) {
      invalidate()
    }
  }

  private fun getInterpolatedDiameter(timeElapsed: Long): Float {
    return if (timeElapsed >= DURATION) {
      if (isExpanding) {
        diameterLarge
      } else {
        diameterSmall
      }
    } else {
      val interpolatedFraction = interpolator.getInterpolation(timeElapsed / DURATION.toFloat())
      if (isExpanding) {
        interpolateFromFraction(interpolatedFraction)
      } else {
        interpolateFromFraction(1 - interpolatedFraction)
      }
    }
  }

  private fun interpolateFromFraction(fraction: Float): Float {
    return diameterSmall + (diameterLarge - diameterSmall) * fraction
  }

  fun expand() {
    if (isExpanding) {
      return
    }

    isExpanding = true
    startTime = System.currentTimeMillis()
    invalidate()
  }

  fun shrink() {
    if (!isExpanding) {
      return
    }

    isExpanding = false
    startTime = System.currentTimeMillis()
    invalidate()
  }

  override fun hitTest(x: Float, y: Float): Boolean {
    val dx = x - buttonCenter[0]
    val dy = y - buttonCenter[1]
    val radius = diameterLarge / 2

    return dx * dx + dy * dy < radius * radius
  }

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {}

  companion object {

    private const val DURATION = 150L

    @JvmField
    val CREATOR: Parcelable.Creator<TrashRenderer> = object : Parcelable.Creator<TrashRenderer> {
      override fun createFromParcel(`in`: Parcel): TrashRenderer {
        return TrashRenderer()
      }

      override fun newArray(size: Int): Array<TrashRenderer?> {
        return arrayOfNulls(size)
      }
    }
  }
}
