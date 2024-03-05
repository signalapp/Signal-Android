package org.thoughtcrime.securesms.scribbles

import android.animation.FloatEvaluator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.ColorUtils
import org.thoughtcrime.securesms.scribbles.HSVColorSlider.toHue
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.customizeOnDraw

/**
 * One stop shop to turn an AppCompatSeekBar into an HSV Color Slider.
 */
object HSVColorSlider {

  private const val MAX_HUE = 360
  private const val COLOR_DIVISIONS = 1023
  private const val BLACK_DIVISIONS = 175
  private const val WHITE_DIVISIONS = 125
  private const val MAX_SEEK_DIVISIONS = COLOR_DIVISIONS + BLACK_DIVISIONS + WHITE_DIVISIONS
  private const val STANDARD_LIGHTNESS = 0.4f
  private val EVALUATOR = FloatEvaluator()

  private val colors: IntArray = (0..BLACK_DIVISIONS).map { value ->
    ColorUtils.HSLToColor(
      floatArrayOf(
        MAX_HUE.toFloat(),
        1f,
        value / BLACK_DIVISIONS.toFloat() * STANDARD_LIGHTNESS
      )
    )
  }.toIntArray() + (0..COLOR_DIVISIONS).map { hue ->
    ColorUtils.HSLToColor(
      floatArrayOf(
        hue.toHue(COLOR_DIVISIONS),
        1f,
        calculateLightness(hue.toFloat(), STANDARD_LIGHTNESS)
      )
    )
  }.toIntArray() + (0..WHITE_DIVISIONS).map { value ->
    ColorUtils.HSLToColor(
      floatArrayOf(
        COLOR_DIVISIONS.toHue(COLOR_DIVISIONS),
        1f,
        EVALUATOR.evaluate(value / WHITE_DIVISIONS.toFloat(), calculateLightness(COLOR_DIVISIONS.toFloat(), STANDARD_LIGHTNESS), 1f)
      )
    )
  }.toIntArray()

  @ColorInt
  fun getLastColor(): Int {
    return colors.last()
  }

  fun AppCompatSeekBar.getColor(): Int {
    return colors[progress]
  }

  fun AppCompatSeekBar.setColor(color: Int) {
    val index = colors.indexOf(color)
    progress = if (index >= 0) {
      index
    } else {
      0
    }
  }

  fun AppCompatSeekBar.setUpForColor(
    @ColorInt thumbBorderColor: Int,
    onColorChanged: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
  ) {
    max = MAX_SEEK_DIVISIONS
    thumb = createThumbDrawable(thumbBorderColor)
    progressDrawable = createColorProgressDrawable()
    setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          val color = colors[progress]
          (thumb as ThumbDrawable).setColor(color)
          onColorChanged(color)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
          onDragStart()
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
          onDragEnd()
        }
      }
    )

    progress = BLACK_DIVISIONS + 1
    (thumb as ThumbDrawable).setColor(colors[progress])
  }

  fun createThumbDrawable(@ColorInt borderColor: Int): Drawable {
    return ThumbDrawable(borderColor)
  }

  private fun createColorProgressDrawable(): Drawable {
    return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).forSeekBar()
  }

  private fun calculateLightness(hue: Float, valueFor60To80: Float = 0.3f): Float {
    val point1 = PointF()
    val point2 = PointF()

    if (hue >= 0f && hue < 60f) {
      point1.set(0f, 0.45f)
      point2.set(60f, valueFor60To80)
    } else if (hue >= 60f && hue < 180f) {
      return valueFor60To80
    } else if (hue >= 180f && hue < 240f) {
      point1.set(180f, valueFor60To80)
      point2.set(240f, 0.5f)
    } else if (hue >= 240f && hue < 300f) {
      point1.set(240f, 0.5f)
      point2.set(300f, 0.4f)
    } else if (hue >= 300f && hue < 360f) {
      point1.set(300f, 0.4f)
      point2.set(360f, 0.45f)
    } else {
      return 0.45f
    }

    return interpolate(point1, point2, hue)
  }

  private fun interpolate(point1: PointF, point2: PointF, x: Float): Float {
    return ((point1.y * (point2.x - x)) + (point2.y * (x - point1.x))) / (point2.x - point1.x)
  }

  private fun Number.toHue(max: Number): Float {
    return Util.clamp(toFloat() * (MAX_HUE / max.toFloat()), 0f, MAX_HUE.toFloat())
  }

  private fun Drawable.forSeekBar(): Drawable {
    val height: Int = ViewUtil.dpToPx(1)
    val radii: FloatArray = (1..8).map { 50f }.toFloatArray()
    val bounds = RectF()
    val clipPath = Path()
    val paint = Paint().apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = ViewUtil.dpToPx(4).toFloat()
    }

    return customizeOnDraw { wrapped, canvas ->
      canvas.save()
      bounds.set(this.bounds)
      bounds.inset(0f, (height / 2f) + 1)

      clipPath.rewind()
      clipPath.addRoundRect(bounds, radii, Path.Direction.CW)

      canvas.drawPath(clipPath, paint)
      canvas.clipPath(clipPath)
      wrapped.draw(canvas)
      canvas.restore()
    }
  }

  private class ThumbDrawable(@ColorInt borderColor: Int) : Drawable() {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = borderColor
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.TRANSPARENT
    }

    private val borderWidth: Int = ViewUtil.dpToPx(THUMB_MARGIN)
    private val thumbInnerSize: Int = ViewUtil.dpToPx(THUMB_INNER_SIZE)
    private val innerRadius: Float = thumbInnerSize / 2f
    private val thumbSize: Float = (thumbInnerSize + borderWidth).toFloat()
    private val thumbRadius: Float = thumbSize / 2f

    override fun getIntrinsicHeight(): Int = ViewUtil.dpToPx(48)

    override fun getIntrinsicWidth(): Int = ViewUtil.dpToPx(48)

    fun setColor(@ColorInt color: Int) {
      paint.color = color
      invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
      canvas.drawCircle(
        (bounds.width() / 2f) + bounds.left,
        (bounds.height() / 2f) + bounds.top,
        thumbRadius,
        borderPaint
      )
      canvas.drawCircle(
        (bounds.width() / 2f) + bounds.left,
        (bounds.height() / 2f) + bounds.top,
        innerRadius,
        paint
      )
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSPARENT

    companion object {
      @Dimension(unit = Dimension.DP)
      private val THUMB_INNER_SIZE = 8

      @Dimension(unit = Dimension.DP)
      private val THUMB_MARGIN = 24
    }
  }
}
