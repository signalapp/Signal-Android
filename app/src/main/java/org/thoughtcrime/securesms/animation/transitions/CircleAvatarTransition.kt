package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TypeEvaluator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator

private const val POSITION_ON_SCREEN = "signal.circleavatartransition.positiononscreen"
private const val WIDTH = "signal.circleavatartransition.width"
private const val HEIGHT = "signal.circleavatartransition.height"

/**
 * Custom transition for Circular avatars, because once you have multiple things animating stuff was getting broken and weird.
 */
class CircleAvatarTransition(context: Context, attrs: AttributeSet?) : Transition(context, attrs) {
  override fun captureStartValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  private fun captureValues(transitionValues: TransitionValues) {
    val view: View = transitionValues.view

    if (view.transitionName == "avatar") {
      val topLeft = intArrayOf(0, 0)
      view.getLocationOnScreen(topLeft)
      transitionValues.values[POSITION_ON_SCREEN] = topLeft
      transitionValues.values[WIDTH] = view.measuredWidth
      transitionValues.values[HEIGHT] = view.measuredHeight
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues == null || endValues == null) {
      return null
    }

    val view: View = endValues.view
    if (view.transitionName != "avatar") {
      return null
    }

    val startCoords: IntArray = startValues.values[POSITION_ON_SCREEN] as? IntArray ?: intArrayOf(0, 0).apply { view.getLocationOnScreen(this) }
    val endCoords: IntArray = endValues.values[POSITION_ON_SCREEN] as? IntArray ?: intArrayOf(0, 0).apply { view.getLocationOnScreen(this) }

    val startWidth: Int = startValues.values[WIDTH] as? Int ?: view.measuredWidth
    val endWidth: Int = endValues.values[WIDTH] as? Int ?: view.measuredWidth

    val startHeight: Int = startValues.values[HEIGHT] as? Int ?: view.measuredHeight
    val endHeight: Int = endValues.values[HEIGHT] as? Int ?: view.measuredHeight

    val startHeightOffset = (endHeight - startHeight) / 2f
    val startWidthOffset = (endWidth - startWidth) / 2f

    val translateXHolder = PropertyValuesHolder.ofFloat("translationX", startCoords[0] - endCoords[0] - startWidthOffset, 0f).apply {
      setEvaluator(FloatInterpolatorEvaluator(DecelerateInterpolator()))
    }
    val translateYHolder = PropertyValuesHolder.ofFloat("translationY", startCoords[1] - endCoords[1] - startHeightOffset, 0f).apply {
      setEvaluator(FloatInterpolatorEvaluator(AccelerateInterpolator()))
    }

    val widthRatio = startWidth.toFloat() / endWidth
    val scaleXHolder = PropertyValuesHolder.ofFloat("scaleX", widthRatio, 1f)

    val heightRatio = startHeight.toFloat() / endHeight
    val scaleYHolder = PropertyValuesHolder.ofFloat("scaleY", heightRatio, 1f)

    return ObjectAnimator.ofPropertyValuesHolder(view, translateXHolder, translateYHolder, scaleXHolder, scaleYHolder)
  }

  private class FloatInterpolatorEvaluator(
    private val interpolator: Interpolator
  ) : TypeEvaluator<Float> {

    override fun evaluate(fraction: Float, startValue: Float, endValue: Float): Float {
      val interpolatedFraction = interpolator.getInterpolation(fraction)
      val delta = endValue - startValue

      return delta * interpolatedFraction + startValue
    }
  }
}
