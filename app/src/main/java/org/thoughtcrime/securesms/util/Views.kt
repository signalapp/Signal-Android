package org.thoughtcrime.securesms.util

import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes

/**
 * Runs the given animation on this view, assuming that the view is in an INVISIBLE or HIDDEN state.
 */
fun View.runRevealAnimation(@AnimRes anim: Int) {
  animation = AnimationUtils.loadAnimation(context, anim)
  visible = true
}

/**
 * Runs the given animation on this view, assuming that the view is in a VISIBLE state and will
 * hide on completion
 */
fun View.runHideAnimation(@AnimRes anim: Int) {
  startAnimation(
    AnimationUtils.loadAnimation(context, anim).apply {
      setListeners(onAnimationEnd = {
        visible = false
      })
    }
  )
}
