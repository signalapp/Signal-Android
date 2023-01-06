package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.FloatEvaluator
import androidx.annotation.FloatRange
import org.signal.core.util.dp
import org.thoughtcrime.securesms.util.Util

/**
 * Centralized location for filter view linear interpolations.
 */
object FilterLerp {

  /**
   * The minimum height of the filter pull when the filter is open.
   */
  const val FILTER_OPEN_HEIGHT = 52f

  /**
   * The maximum height of the filter pull. Note that this should match
   * whatever value is set to in XML.
   */
  private const val FILTER_APEX = 130f

  private val EVAL = FloatEvaluator()

  private val PILL_LERP = getFn(
    Point(FILTER_OPEN_HEIGHT / FILTER_APEX, 0f),
    Point(1f, ((FILTER_OPEN_HEIGHT - FILTER_APEX) / 2))
  )

  private val OPEN_CIRCLE_BOTTOM_PAD_LERP = getFn(
    Point(FILTER_OPEN_HEIGHT / FILTER_APEX, 8f),
    Point(1f, FILTER_APEX * 0.55f)
  )

  private val CLOSED_CIRCLE_BOTTOM_PAD_LERP = getFn(
    Point(0f, 0f),
    Point(1f, FILTER_APEX * 0.55f)
  )

  private val PILL_CLOSE_APEX_ALPHA_LERP = getFn(
    Point(0.35f * FILTER_APEX / 100, 0f),
    Point(0.7f * FILTER_APEX / 100, 1f)
  )

  private val CIRCLE_CANCEL_ALPHA_LERP = getFn(
    Point(0.61f, 1f),
    Point(0.43f, 0f)
  )

  private fun helpTextAlphaLerp(@FloatRange(from = 0.0, to = 1.0) startFraction: Float) = getFn(
    Point(startFraction, 0f),
    Point(1f, 1f)
  )

  fun getHelpTextAlphaLerp(fraction: Float, startFraction: Float): Float {
    return getLerp(fraction, helpTextAlphaLerp(startFraction))
  }

  fun getPillCloseApexAlphaLerp(fraction: Float): Float {
    return Util.clamp(getLerp(fraction, PILL_CLOSE_APEX_ALPHA_LERP), 0f, 1f)
  }

  fun getCircleCancelAlphaLerp(fraction: Float): Float {
    return Util.clamp(getLerp(fraction, CIRCLE_CANCEL_ALPHA_LERP), 0f, 1f)
  }

  /**
   * Get the LERP for the "Filter enabled" pill.
   */
  fun getPillLerp(fraction: Float): Float {
    return getLerp(fraction, PILL_LERP)
  }

  /**
   * Get the LERP for the padding below the filter circle when the filter is open
   */
  fun getOpenCircleBottomPadLerp(fraction: Float): Float {
    return getLerp(fraction, OPEN_CIRCLE_BOTTOM_PAD_LERP)
  }

  /**
   * Get the LERP for the padding below the filter circle when the filter is closed
   */
  fun getClosedCircleBottomPadLerp(fraction: Float): Float {
    return getLerp(fraction, CLOSED_CIRCLE_BOTTOM_PAD_LERP)
  }

  private fun getLerp(fraction: Float, fn: Fn): Float {
    return EVAL.evaluate(fraction, fn(0f), fn(1f)).dp
  }

  /**
   * Gets the linear slope between two points using:
   *
   * m = (y2 - y1) / (x2 - x1)
   */
  private fun getSlope(
    a: Point,
    b: Point
  ): Float = (b.y - a.y) / (b.x - a.x)

  /**
   * Gets the y-intercept between two points using:
   *
   * b = y - mx
   */
  private fun getYIntercept(
    a: Point,
    b: Point
  ): Float = a.y - getSlope(a, b) * a.x

  /**
   * For a given set of points, generates a function in the form
   *
   * y = mx + b
   */
  private fun getFn(
    a: Point,
    b: Point
  ): Fn = Fn(getSlope(a, b), getYIntercept(a, b))

  /**
   * 2D cartesian coordinate.
   */
  data class Point(val x: Float, val y: Float)

  /**
   * LERP function defined as y = mx + b
   */
  data class Fn(val m: Float, val b: Float) {
    operator fun invoke(x: Float): Float {
      return m * x + b
    }
  }
}
