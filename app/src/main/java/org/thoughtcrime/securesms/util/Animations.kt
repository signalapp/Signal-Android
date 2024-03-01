package org.thoughtcrime.securesms.util

import android.view.animation.Animation
import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat

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

fun createDefaultCubicBezierInterpolator(): Interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
