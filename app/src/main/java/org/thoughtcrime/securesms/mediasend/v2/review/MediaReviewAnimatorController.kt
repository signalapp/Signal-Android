package org.thoughtcrime.securesms.mediasend.v2.review

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.Interpolator
import androidx.core.animation.doOnEnd
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.util.visible

object MediaReviewAnimatorController {

  fun getFadeInAnimator(view: View, interpolator: Interpolator = MediaAnimations.interpolator, isEnabled: Boolean = true): Animator {
    view.visible = true
    view.isEnabled = isEnabled

    return ObjectAnimator.ofFloat(view, "alpha", view.alpha, 1f).apply {
      setInterpolator(interpolator)
    }
  }

  fun getFadeOutAnimator(view: View, interpolator: Interpolator = MediaAnimations.interpolator, isEnabled: Boolean = false): Animator {
    view.isEnabled = isEnabled

    val animator = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
      setInterpolator(interpolator)
    }

    animator.doOnEnd { view.visible = false }

    return animator
  }

  fun getHeightAnimator(view: View, start: Int, end: Int, interpolator: Interpolator = MediaAnimations.interpolator): Animator {
    return ValueAnimator.ofInt(start, end).apply {
      setInterpolator(interpolator)
      addUpdateListener {
        val animatedValue = it.animatedValue as Int
        val layoutParams = view.layoutParams
        layoutParams.height = animatedValue
        view.layoutParams = layoutParams
      }
      duration = 120
    }
  }
}
