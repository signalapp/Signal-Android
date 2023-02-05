package org.thoughtcrime.securesms.scribbles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.Px
import org.thoughtcrime.securesms.util.ViewUtil
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class RotationDialView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private val canvasBounds = Rect()
  private val centerMostIndicatorRect = RectF()
  private val indicatorRect = RectF()
  private val dimensions = Dimensions()

  private var snapDegrees: Float = 0f
  private var degrees: Float = 0f
  private var isInGesture: Boolean = false

  private val gestureDetector: GestureDetector = GestureDetector(context, GestureListener())

  var listener: Listener? = null

  private val textPaint = Paint().apply {
    isAntiAlias = true
    textSize = ViewUtil.spToPx(15f).toFloat()
    typeface = Typeface.DEFAULT
    color = Colors.textColor
    style = Paint.Style.FILL
    textAlign = Paint.Align.CENTER
  }

  private val angleIndicatorPaint = Paint().apply {
    isAntiAlias = true
    color = Color.WHITE
    style = Paint.Style.FILL
  }

  fun setDegrees(degrees: Float) {
    if (degrees != this.degrees) {
      this.degrees = degrees
      this.snapDegrees = calculateSnapDegrees()

      if (isInGesture) {
        listener?.onDegreeChanged(snapDegrees)
      }

      invalidate()
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionIndex != 0) return false

    isInGesture = gestureDetector.onTouchEvent(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> listener?.onGestureStart()
      MotionEvent.ACTION_UP -> listener?.onGestureEnd()
    }

    return isInGesture
  }

  override fun onDraw(canvas: Canvas) {
    if (isInEditMode) {
      canvas.drawColor(Color.BLACK)
    }

    canvas.getClipBounds(canvasBounds)

    val dialDegrees = getDialDegrees(snapDegrees)
    val bottom = canvasBounds.bottom
    val approximateCenterDegree = dialDegrees.roundToInt()
    var currentDegree = approximateCenterDegree
    val fractionalOffset = dialDegrees - approximateCenterDegree
    val dialOffset = dimensions.spaceBetweenAngleIndicators * fractionalOffset

    val centerX = width / 2f
    centerMostIndicatorRect.set(
      centerX - dimensions.angleIndicatorWidth / 2f,
      bottom.toFloat() - dimensions.majorAngleIndicatorHeight,
      centerX + dimensions.angleIndicatorWidth / 2f,
      bottom.toFloat()
    )
    centerMostIndicatorRect.offset(-dialOffset, 0f)

    indicatorRect.set(centerMostIndicatorRect)

    angleIndicatorPaint.color = Colors.colorForOtherDegree(currentDegree)
    indicatorRect.top = bottom.toFloat() - dimensions.getHeightForDegree(currentDegree)
    canvas.drawRect(indicatorRect, angleIndicatorPaint)
    indicatorRect.offset(dimensions.spaceBetweenAngleIndicators.toFloat(), 0f)
    currentDegree += 1

    while (indicatorRect.left < width && currentDegree <= ceil(MAX_DEGREES)) {
      angleIndicatorPaint.color = Colors.colorForOtherDegree(currentDegree)
      indicatorRect.top = bottom.toFloat() - dimensions.getHeightForDegree(currentDegree)
      canvas.drawRect(indicatorRect, angleIndicatorPaint)
      indicatorRect.offset(dimensions.spaceBetweenAngleIndicators.toFloat(), 0f)
      currentDegree += 1
    }

    currentDegree = approximateCenterDegree
    indicatorRect.set(centerMostIndicatorRect)
    indicatorRect.offset(-dimensions.spaceBetweenAngleIndicators.toFloat(), 0f)
    currentDegree -= 1

    while (indicatorRect.left >= 0 && currentDegree >= floor(MIN_DEGRESS)) {
      angleIndicatorPaint.color = Colors.colorForOtherDegree(currentDegree)
      indicatorRect.top = bottom.toFloat() - dimensions.getHeightForDegree(currentDegree)
      canvas.drawRect(indicatorRect, angleIndicatorPaint)
      indicatorRect.offset(-dimensions.spaceBetweenAngleIndicators.toFloat(), 0f)
      currentDegree -= 1
    }

    centerMostIndicatorRect.offset(dialOffset, 0f)
    angleIndicatorPaint.color = Colors.colorForCenterDegree(approximateCenterDegree)
    canvas.drawRect(centerMostIndicatorRect, angleIndicatorPaint)

    drawText(canvas)
  }

  private fun drawText(canvas: Canvas) {
    val approximateDegrees = getDialDegrees(snapDegrees).roundToInt()
    canvas.drawText(
      "$approximateDegrees",
      width / 2f,
      canvasBounds.bottom - textPaint.descent() - dimensions.majorAngleIndicatorHeight - dimensions.textPaddingBottom,
      textPaint
    )
  }

  private fun getDialDegrees(degrees: Float): Float {
    val alpha: Float = degrees % 360f

    if (alpha % 90 == 0f) {
      return 0f
    }

    val beta: Float = floor(alpha / 90f)
    val offset: Float = alpha - beta * 90f

    return if (offset > 45f) {
      offset - 90f
    } else {
      offset
    }
  }

  private fun calculateSnapDegrees(): Float {
    return if (isInGesture) {
      val dialDegrees = getDialDegrees(degrees)
      if (dialDegrees.roundToInt() == 0) {
        degrees - dialDegrees
      } else {
        degrees
      }
    } else {
      degrees
    }
  }

  private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
      return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
      val degreeIncrement: Float = distanceX / dimensions.spaceBetweenAngleIndicators
      val prevDialDegrees = getDialDegrees(degrees)
      val newDialDegrees = getDialDegrees(degrees + degreeIncrement)

      val offEndOfMax = prevDialDegrees >= MAX_DEGREES / 2f && newDialDegrees <= MIN_DEGRESS / 2f
      val offEndOfMin = newDialDegrees >= MAX_DEGREES / 2f && prevDialDegrees <= MIN_DEGRESS / 2f

      if (prevDialDegrees.roundToInt() != newDialDegrees.roundToInt() && isHapticFeedbackEnabled) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
      }

      when {
        offEndOfMax -> {
          val newIncrement = MAX_DEGREES - prevDialDegrees
          setDegrees(degrees + newIncrement)
        }
        offEndOfMin -> {
          val newIncrement = MAX_DEGREES - abs(prevDialDegrees)
          setDegrees(degrees - newIncrement)
        }
        else -> {
          setDegrees(degrees + degreeIncrement)
        }
      }

      return true
    }
  }

  private class Dimensions {

    @Px
    val spaceBetweenAngleIndicators: Int = ViewUtil.dpToPx(Dimensions.spaceBetweenAngleIndicators)

    @Px
    val angleIndicatorWidth: Int = ViewUtil.dpToPx(Dimensions.angleIndicatorWidth)

    @Px
    val minorAngleIndicatorHeight: Int = ViewUtil.dpToPx(Dimensions.minorAngleIndicatorHeight)

    @Px
    val majorAngleIndicatorHeight: Int = ViewUtil.dpToPx(Dimensions.majorAngleIndicatorHeight)

    @Px
    val textPaddingBottom: Int = ViewUtil.dpToPx(Dimensions.textPaddingBottom)

    fun getHeightForDegree(degree: Int): Int {
      return if (degree == 0) {
        majorAngleIndicatorHeight
      } else {
        minorAngleIndicatorHeight
      }
    }

    companion object {

      @Dimension(unit = Dimension.DP)
      private val spaceBetweenAngleIndicators: Int = 12

      @Dimension(unit = Dimension.DP)
      private val angleIndicatorWidth: Int = 1

      @Dimension(unit = Dimension.DP)
      private val minorAngleIndicatorHeight: Int = 12

      @Dimension(unit = Dimension.DP)
      private val majorAngleIndicatorHeight: Int = 32

      @Dimension(unit = Dimension.DP)
      private val textPaddingBottom: Int = 8
    }
  }

  private object Colors {
    @ColorInt
    val textColor: Int = Color.WHITE

    @ColorInt
    val majorAngleIndicatorColor: Int = 0xFF62E87A.toInt()

    @ColorInt
    val modFiveIndicatorColor: Int = Color.WHITE

    @ColorInt
    val minorAngleIndicatorColor: Int = 0x80FFFFFF.toInt()

    fun colorForCenterDegree(degree: Int) = if (degree == 0) modFiveIndicatorColor else majorAngleIndicatorColor

    fun colorForOtherDegree(degree: Int): Int {
      return when {
        degree % 5 == 0 -> modFiveIndicatorColor
        else -> minorAngleIndicatorColor
      }
    }
  }

  companion object {
    private const val MAX_DEGREES: Float = 44.99999f
    private const val MIN_DEGRESS: Float = -44.99999f
  }

  interface Listener {
    fun onDegreeChanged(degrees: Float)
    fun onGestureStart()
    fun onGestureEnd()
  }
}
