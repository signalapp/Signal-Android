package org.thoughtcrime.securesms.stories

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder.ofFloat
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat

class ScaleTransition : Transition {

  companion object {

    private const val LAYOUT_WIDTH = "ScaleTransition:layout_width"
    private const val LAYOUT_HEIGHT = "ScaleTransition:layout_height"
    private const val POSITION_X = "ScaleTransition:position_x"
    private const val POSITION_Y = "ScaleTransition:position_y"
    private const val SCALE_X = "ScaleTransition:scale_x"
    private const val SCALE_Y = "ScaleTransition:scale_y"

    private val PROPERTIES = arrayOf(
      LAYOUT_WIDTH,
      LAYOUT_HEIGHT,
      POSITION_X,
      POSITION_Y,
      SCALE_X,
      SCALE_Y
    )
  }

  constructor() : super()

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

  override fun getTransitionProperties(): Array<String> {
    return PROPERTIES
  }

  override fun captureStartValues(transitionValues: TransitionValues) {
    if (ViewCompat.getTransitionName(transitionValues.view) == "story") {
      captureValues(transitionValues)
    }
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    if (ViewCompat.getTransitionName(transitionValues.view) == "story") {
      resetValues(transitionValues.view)
      captureValues(transitionValues)
    }
  }

  private fun captureValues(transitionValues: TransitionValues) = with(transitionValues.view) {
    transitionValues.values[LAYOUT_WIDTH] = width.toFloat()
    transitionValues.values[LAYOUT_HEIGHT] = height.toFloat()
    transitionValues.values[POSITION_X] = x
    transitionValues.values[POSITION_Y] = y
    transitionValues.values[SCALE_X] = scaleX
    transitionValues.values[SCALE_Y] = scaleY
  }

  private fun resetValues(view: View) = with(view) {
    translationX = 0f
    translationY = 0f
    scaleX = 1f
    scaleY = 1f
  }

  override fun createAnimator(
    sceneRoot: ViewGroup,
    start: TransitionValues?,
    end: TransitionValues?
  ): Animator? {
    if (start == null || end == null) {
      return null
    }

    val startWidth = start.values[LAYOUT_WIDTH] as Float
    val endWidth = end.values[LAYOUT_WIDTH] as Float
    val startHeight = start.values[LAYOUT_HEIGHT] as Float
    val endHeight = end.values[LAYOUT_HEIGHT] as Float

    val startX = start.values[POSITION_X] as Float
    val endX = end.values[POSITION_X] as Float
    val startY = start.values[POSITION_Y] as Float
    val endY = end.values[POSITION_Y] as Float

    val startScaleX = start.values[SCALE_X] as Float
    val startScaleY = start.values[SCALE_Y] as Float

    end.view.translationX = (startX - endX) - (endWidth - startWidth) / 2
    end.view.translationY = (startY - endY) - (endHeight - startHeight) / 2

    end.view.scaleX = (startWidth / endWidth) * startScaleX
    end.view.scaleY = (startHeight / endHeight) * startScaleY

    return ObjectAnimator.ofPropertyValuesHolder(
      end.view,
      ofFloat(View.TRANSLATION_X, 0f),
      ofFloat(View.TRANSLATION_Y, 0f),
      ofFloat(View.SCALE_X, 1f),
      ofFloat(View.SCALE_Y, 1f)
    ).apply {
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          resetValues(start.view)
          resetValues(end.view)
        }
      })
    }
  }
}
