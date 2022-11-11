package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FabElevationFadeTransform(context: Context, attrs: AttributeSet) : Transition(context, attrs) {

  companion object {
    private const val ELEVATION = "CrossfaderTransition.ELEVATION"
  }

  override fun captureStartValues(transitionValues: TransitionValues) {
    if (transitionValues.view is FloatingActionButton) {
      transitionValues.values[ELEVATION] = ViewCompat.getElevation(transitionValues.view)
    }
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    if (transitionValues.view is FloatingActionButton) {
      transitionValues.values[ELEVATION] = ViewCompat.getElevation(transitionValues.view)
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    if (startValues?.view !is FloatingActionButton || endValues?.view !is FloatingActionButton) {
      return null
    }

    val startElevation = ViewCompat.getElevation(startValues.view)
    val endElevation = ViewCompat.getElevation(endValues.view)
    if (startElevation == endElevation) {
      return null
    }

    return ValueAnimator.ofFloat(
      startValues.values[ELEVATION] as Float,
      endValues.values[ELEVATION] as Float
    ).apply {
      addUpdateListener {
        val elevation = it.animatedValue as Float
        ViewCompat.setElevation(endValues.view, elevation)
      }
    }
  }
}
