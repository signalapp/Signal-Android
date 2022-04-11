package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionValues
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.google.android.material.floatingactionbutton.FloatingActionButton

@RequiresApi(21)
class FabElevationFadeTransform(context: Context, attrs: AttributeSet?) : Transition(context, attrs) {

  companion object {
    private const val ELEVATION = "CrossfaderTransition.ELEVATION"
  }

  override fun captureStartValues(transitionValues: TransitionValues) {
    if (transitionValues.view is FloatingActionButton) {
      transitionValues.values[ELEVATION] = transitionValues.view.elevation
    }
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    if (transitionValues.view is FloatingActionButton) {
      transitionValues.values[ELEVATION] = transitionValues.view.elevation
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup?, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues?.view !is FloatingActionButton || endValues?.view !is FloatingActionButton) {
      return null
    }

    val startElevation = startValues.view.elevation
    val endElevation = endValues.view.elevation
    if (startElevation == endElevation) {
      return null
    }

    return ValueAnimator.ofFloat(
      startValues.values[ELEVATION] as Float,
      endValues.values[ELEVATION] as Float
    ).apply {
      addUpdateListener {
        val elevation = it.animatedValue as Float
        endValues.view.elevation = elevation
      }
    }
  }
}
