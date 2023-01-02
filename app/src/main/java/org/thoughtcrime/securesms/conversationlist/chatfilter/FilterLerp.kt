package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.animation.FloatEvaluator
import org.signal.core.util.dp

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
