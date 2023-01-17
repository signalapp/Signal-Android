package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.RectEvaluator
import android.content.Context
import android.graphics.Rect
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.fragment.app.FragmentContainerView

private const val BOUNDS = "signal.wipedowntransition.bottom"

/**
 * WipeDownTransition will animate the bottom position of a view such that it "wipes" down the screen to a final position.
 */
class WipeDownTransition(context: Context, attrs: AttributeSet?) : Transition(context, attrs) {
  override fun captureStartValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    captureValues(transitionValues)
  }

  private fun captureValues(transitionValues: TransitionValues) {
    val view: View = transitionValues.view

    if (view is ViewGroup) {
      val rect = Rect()
      view.getLocalVisibleRect(rect)
      transitionValues.values[BOUNDS] = rect
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues == null || endValues == null) {
      return null
    }

    val view: View = endValues.view
    if (view !is FragmentContainerView) {
      return null
    }

    val startBottom: Rect = startValues.values[BOUNDS] as? Rect ?: Rect().apply { view.getLocalVisibleRect(this) }
    val endBottom: Rect = endValues.values[BOUNDS] as? Rect ?: Rect().apply { view.getLocalVisibleRect(this) }

    return ObjectAnimator.ofObject(view, "clipBounds", RectEvaluator(), startBottom, endBottom).apply {
      addListener(
        onEnd = {
          view.clipBounds = null
        }
      )
    }
  }
}
