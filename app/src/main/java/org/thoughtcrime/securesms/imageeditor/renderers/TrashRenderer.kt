package org.thoughtcrime.securesms.imageeditor.renderers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.appcompat.content.res.AppCompatResources
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.imageeditor.Bounds
import org.thoughtcrime.securesms.imageeditor.Renderer
import org.thoughtcrime.securesms.imageeditor.RendererContext
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.util.ViewUtil

internal class TrashRenderer : InvalidateableRenderer, Renderer, Parcelable {

  private val outlinePaint = Paint().apply {
    isAntiAlias = true
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = ViewUtil.dpToPx(15) / 10f
  }

  private val shadePaint = Paint().apply {
    isAntiAlias = true
    color = 0x99000000.toInt()
    style = Paint.Style.FILL
  }

  private val bounds = RectF()

  private val diameterSmall = ViewUtil.dpToPx(41)
  private val diameterLarge = ViewUtil.dpToPx(54)
  private val trashSize = ViewUtil.dpToPx(24)
  private val padBottom = ViewUtil.dpToPx(16)

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
    rendererContext.canvas.translate(- (trashSize / 2f), - (trashSize / 2f))
    trash.draw(rendererContext.canvas)
    rendererContext.canvas.restore()

    if (frameRenderTime - DURATION < startTime) {
      invalidate()
    }
  }

  private fun getInterpolatedDiameter(timeElapsed: Long): Float {
    return if (timeElapsed >= DURATION) {
      if (isExpanding) {
        diameterLarge.toFloat()
      } else {
        diameterSmall.toFloat()
      }
    } else {
      val interpolatedFraction = MediaAnimations.interpolator.getInterpolation(timeElapsed / DURATION.toFloat())
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

  private constructor(inParcel: Parcel?)

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
        return TrashRenderer(`in`)
      }

      override fun newArray(size: Int): Array<TrashRenderer?> {
        return arrayOfNulls(size)
      }
    }
  }
}
