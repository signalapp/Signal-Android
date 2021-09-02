package org.thoughtcrime.securesms.mediasend.v2

import android.animation.Animator
import android.view.View
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.util.visible

object MediaAnimations {
  private const val FADE_ANIMATION_DURATION = 150L

  fun fadeIn(view: View) {
    if (view.visible) {
      return
    }

    view.visible = true
    view.animate()
      .setDuration(FADE_ANIMATION_DURATION)
      .alpha(1f)
  }

  fun fadeOut(view: View) {
    if (!view.visible) {
      return
    }

    view.animate()
      .setDuration(FADE_ANIMATION_DURATION)
      .setListener(object : AnimationCompleteListener() {
        override fun onAnimationEnd(animation: Animator?) {
          view.visible = false
        }
      })
      .alpha(0f)
  }

  fun fade(view: View, fadeIn: Boolean) {
    if (fadeIn) {
      fadeIn(view)
    } else {
      fadeOut(view)
    }
  }
}
