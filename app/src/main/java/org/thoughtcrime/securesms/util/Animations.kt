package org.thoughtcrime.securesms.util

import android.view.animation.Animation

fun Animation.setListeners(
  onAnimationStart: (animation: Animation?) -> Unit = { },
  onAnimationEnd: (animation: Animation?) -> Unit = { },
  onAnimationRepeat: (animation: Animation?) -> Unit = { }
) {
  this.setAnimationListener(object : Animation.AnimationListener {
    override fun onAnimationStart(animation: Animation?) {
      onAnimationStart(animation)
    }

    override fun onAnimationEnd(animation: Animation?) {
      onAnimationEnd(animation)
    }

    override fun onAnimationRepeat(animation: Animation?) {
      onAnimationRepeat(animation)
    }
  })
}
