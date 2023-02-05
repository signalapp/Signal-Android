package org.thoughtcrime.securesms.mediasend.v2.review

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.visible

object MediaReviewAnimatorController {

  fun getSlideInAnimator(view: View): Animator {
    return if (ContextUtil.getAnimationScale(view.context) == 0f) {
      view.translationY = 0f
      ValueAnimator.ofFloat(0f, 1f)
    } else {
      ObjectAnimator.ofFloat(view, "translationY", view.translationY, 0f)
    }
  }

  fun getFadeInAnimator(view: View, isEnabled: Boolean = true): Animator {
    view.visible = true
    view.isEnabled = isEnabled

    return ObjectAnimator.ofFloat(view, "alpha", view.alpha, 1f)
  }

  fun getFadeOutAnimator(view: View, isEnabled: Boolean = false): Animator {
    view.isEnabled = isEnabled

    val animator = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

    animator.doOnEnd { view.visible = false }

    return animator
  }
}
