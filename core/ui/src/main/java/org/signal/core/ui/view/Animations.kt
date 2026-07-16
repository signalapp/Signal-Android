/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.view

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
