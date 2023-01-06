package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Renders the filter-circle at any given position
 *
 * Animation Spec:
 *
 * @ 35dp display open, we want to start animating the first stroke:
 *  - duration 100ms
 *  - curve Quad in/out
 *
 * @ 50dp display open, we want to start animating the second stroke:
 *  - duration 150ms
 *  - curve Quad in/out
 *
 * @ 75dp display open, we want to start animating the third stroke:
 *  - duration 150ms
 *  - curve Quad in/out
 *
 * @ 100dp display open, we want to apply "active" coloring.
 *
 * On release, if active, we transform  into a rounded rectangle
 *  - 38pt circle
 *  - rectangle width 154, height 32
 *  - duration 100ms
 *  - fade in button and text 300ms *after* circle-rectangle animation has completed.
 */
class FilterCircleView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  companion object {
    private val CIRCLE_Y_EVALUATOR = FloatEvaluator()
    private val COLOR_EVALUATOR = ArgbEvaluatorCompat.getInstance()

    private val STROKES = listOf(
      Stroke(
        triggerPoint = 0.35f,
        width = 4.dp,
        distanceFromBottomOfCircle = 11.dp,
        animationDuration = 100.milliseconds
      ),
      Stroke(
        triggerPoint = 0.5f,
        width = 12.dp,
        distanceFromBottomOfCircle = 17.dp,
        animationDuration = 150.milliseconds
      ),
      Stroke(
        triggerPoint = 0.75f,
        width = 18.dp,
        distanceFromBottomOfCircle = 23.dp,
        animationDuration = 150.milliseconds
      )
    )
  }

  private val circleRadius = 38.dp / 2f
  private val circleBackgroundColor = ContextCompat.getColor(context, R.color.signal_colorSurface1)
  private val strokeColor = ContextCompat.getColor(context, R.color.signal_colorSecondary)
  private val circleActiveBackgroundColor = ContextCompat.getColor(context, R.color.signal_colorSecondaryContainer)
  private val strokeActiveColor = ContextCompat.getColor(context, R.color.signal_colorPrimary)

  private var circleColorAnimator: ValueAnimator? = null
  private var strokeColorAnimator: ValueAnimator? = null
  private var circleToRectangleAnimator: ValueAnimator? = null

  private val runningStrokeAnimations = mutableMapOf<Stroke, ValueAnimator>()

  private val circlePaint = Paint().apply {
    isAntiAlias = true
    color = circleBackgroundColor
    style = Paint.Style.FILL
  }

  private val strokePaint = Paint().apply {
    isAntiAlias = true
    color = strokeColor
    style = Paint.Style.FILL
  }

  var progress: Float = 0f
    set(value) {
      field = value
      onStateChange()
    }

  var state: FilterPullState = FilterPullState.CLOSED
    set(value) {
      field = value
      onStateChange()
    }

  var textFieldMetrics: Pair<Int, Int> = Pair(0, 0)

  private val rect = Rect()
  private val rectF = RectF()
  var bottomOffset: Float = evaluateBottomOffset(0f, FilterPullState.CLOSED)

  override fun draw(canvas: Canvas) {
    super.draw(canvas)
    canvas.getClipBounds(rect)

    val centerX = rect.width() / 2f
    val circleBottom = rect.height() - bottomOffset
    val circleCenterY = circleBottom - circleRadius

    val circleShapeAnimator = circleToRectangleAnimator
    if (circleShapeAnimator != null) {
      val (textWidth, textHeight) = textFieldMetrics
      rectF.set(
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedValue as Float, centerX - circleRadius, centerX - (textWidth / 2)),
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedFraction, circleCenterY - circleRadius, circleCenterY - (textHeight / 2)),
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedValue as Float, centerX + circleRadius, centerX + (textWidth / 2)),
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedFraction, circleCenterY + circleRadius, circleCenterY + (textHeight / 2))
      )

      canvas.drawRoundRect(
        rectF,
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedFraction, circleRadius, 8.dp),
        CIRCLE_Y_EVALUATOR.evaluate(circleShapeAnimator.animatedFraction, circleRadius, 8.dp),
        getCirclePaint()
      )
    } else {
      rectF.set(
        centerX - circleRadius,
        circleBottom - circleRadius * 2,
        centerX + circleRadius,
        circleBottom
      )

      canvas.drawRoundRect(
        rectF,
        circleRadius,
        circleRadius,
        getCirclePaint()
      )
    }

    runningStrokeAnimations.forEach { (stroke, animator) ->
      stroke.fillRect(rect, centerX, circleBottom, animator.animatedFraction)
      rectF.set(rect)
      canvas.drawRoundRect(rectF, 50f, 50f, getStrokePaint())
    }
  }

  private fun onStateChange() {
    bottomOffset = evaluateBottomOffset(progress, state)
    checkStrokeTriggers(progress)
    checkColorAnimators(state)
    checkCircleToRectangleAnimator(state)
    invalidate()
  }

  private fun evaluateBottomOffset(progress: Float, state: FilterPullState): Float {
    return when (state) {
      FilterPullState.OPENING, FilterPullState.OPEN, FilterPullState.CLOSE_APEX, FilterPullState.CANCELING, FilterPullState.OPEN_APEX -> FilterLerp.getOpenCircleBottomPadLerp(progress)
      FilterPullState.CLOSED, FilterPullState.CLOSING -> FilterLerp.getClosedCircleBottomPadLerp(progress)
    }
  }

  private fun checkColorAnimators(state: FilterPullState) {
    if (state != FilterPullState.CLOSED && state != FilterPullState.CANCELING) {
      if (circleColorAnimator == null) {
        circleColorAnimator = ValueAnimator
          .ofInt(circleBackgroundColor, circleActiveBackgroundColor).apply {
            addUpdateListener { invalidate() }
            setEvaluator(COLOR_EVALUATOR)
            duration = 200
            start()
          }
      }

      if (strokeColorAnimator == null) {
        strokeColorAnimator = ValueAnimator
          .ofInt(strokeColor, strokeActiveColor).apply {
            addUpdateListener { invalidate() }
            setEvaluator(COLOR_EVALUATOR)
            duration = 200
            start()
          }
      }
    } else {
      circleColorAnimator?.cancel()
      circleColorAnimator = null

      strokeColorAnimator?.cancel()
      strokeColorAnimator = null
    }
  }

  private fun checkStrokeTriggers(progress: Float) {
    if (progress <= 0f) {
      runningStrokeAnimations.forEach { it.value.cancel() }
      runningStrokeAnimations.clear()
      return
    }

    STROKES
      .filter { it.triggerPoint <= progress && !runningStrokeAnimations.containsKey(it) }
      .forEach {
        runningStrokeAnimations[it] = ValueAnimator.ofFloat(0f, 1f).apply {
          addUpdateListener { invalidate() }
          duration = it.animationDuration.inWholeMilliseconds
          start()
        }
      }
  }

  private fun checkCircleToRectangleAnimator(state: FilterPullState) {
    if (state == FilterPullState.OPENING && circleToRectangleAnimator == null) {
      require(textFieldMetrics != Pair(0, 0))
      circleToRectangleAnimator = ValueAnimator.ofFloat(1f).apply {
        addUpdateListener { invalidate() }
        interpolator = OvershootInterpolator()
        startDelay = 100
        duration = 200
        start()
      }
    } else if (state == FilterPullState.CLOSED) {
      circleToRectangleAnimator?.cancel()
      circleToRectangleAnimator = null
    }
  }

  private fun getCirclePaint(): Paint {
    val circleAlpha = when (state) {
      FilterPullState.CLOSED -> 255
      FilterPullState.OPEN_APEX -> 255
      FilterPullState.CANCELING -> 255
      FilterPullState.OPENING -> 255
      FilterPullState.OPEN -> 0
      FilterPullState.CLOSE_APEX -> 0
      FilterPullState.CLOSING -> 0
    }

    return circlePaint.apply {
      color = (circleColorAnimator?.animatedValue ?: circleBackgroundColor) as Int
      alpha = circleAlpha
    }
  }

  private fun getStrokePaint(): Paint {
    val strokeAlpha = max(0f, 1f - (circleToRectangleAnimator?.animatedFraction ?: 0f))

    return strokePaint.apply {
      color = (strokeColorAnimator?.animatedValue ?: strokeColor) as Int
      alpha = (strokeAlpha * 255).toInt()
    }
  }

  private data class Stroke(
    val triggerPoint: Float,
    @Px val width: Int,
    @Px val distanceFromBottomOfCircle: Int,
    val animationDuration: Duration
  ) {
    fun fillRect(rect: Rect, centerX: Float, circleBottom: Float, progress: Float) {
      rect.setEmpty()

      val width = progress * this.width
      if (width <= 0f) {
        return
      }

      rect.bottom = (circleBottom.toInt() - distanceFromBottomOfCircle)
      rect.top = rect.bottom - 2.dp
      rect.left = (centerX - (width / 2f)).toInt()
      rect.right = (centerX + (width / 2f)).toInt()
    }
  }
}
