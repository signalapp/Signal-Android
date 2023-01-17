package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart

class CrossfaderTransition(context: Context, attrs: AttributeSet?) : Transition(context, attrs) {

  companion object {
    private const val WIDTH = "CrossfaderTransition.WIDTH"
  }

  override fun captureStartValues(transitionValues: TransitionValues) {
    if (transitionValues.view is Crossfadeable) {
      transitionValues.values[WIDTH] = transitionValues.view.width
    }
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    if (transitionValues.view is Crossfadeable) {
      transitionValues.values[WIDTH] = transitionValues.view.width
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues == null || endValues == null) {
      return null
    }

    val startWidth = (startValues.values[WIDTH] ?: 0) as Int
    val endWidth = (endValues.values[WIDTH] ?: 0) as Int
    val view: Crossfadeable = endValues.view as? Crossfadeable ?: return null
    val reverse = startWidth > endWidth

    return ValueAnimator.ofFloat(0f, 1f).apply {
      addUpdateListener {
        view.onCrossfadeAnimationUpdated(it.animatedValue as Float, reverse)
      }

      doOnStart {
        view.onCrossfadeStarted(reverse)
      }

      doOnEnd {
        view.onCrossfadeFinished(reverse)
      }
    }
  }

  interface Crossfadeable {
    fun onCrossfadeAnimationUpdated(progress: Float, reverse: Boolean)
    fun onCrossfadeStarted(reverse: Boolean)
    fun onCrossfadeFinished(reverse: Boolean)
  }
}
