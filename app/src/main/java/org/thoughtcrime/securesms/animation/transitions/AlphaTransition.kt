package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.transition.Transition
import androidx.transition.TransitionValues

private const val ALPHA = "signal.alpha_transition.alpha"

/**
 * Alpha transition that can be used with [ConstraintLayout]
 */
class AlphaTransition : Transition() {

  override fun captureStartValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  private fun captureValues(transitionValues: TransitionValues) {
    val view: View = transitionValues.view
    if (view !is ConstraintLayout) {
      transitionValues.values[ALPHA] = view.alpha
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues == null || endValues == null) {
      return null
    }

    val view: View = endValues.view
    val startAlpha: Float = startValues.values[ALPHA] as? Float ?: view.alpha
    val endAlpha: Float = endValues.values[ALPHA] as? Float ?: view.alpha

    return ObjectAnimator.ofFloat(view, "alpha", startAlpha, endAlpha)
  }
}
