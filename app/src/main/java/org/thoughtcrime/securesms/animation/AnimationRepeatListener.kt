/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.animation

import android.animation.Animator
import java.util.function.Consumer

class AnimationRepeatListener(private val animationConsumer: Consumer<Animator>) : Animator.AnimatorListener {
  override fun onAnimationStart(animation: Animator) = Unit
  override fun onAnimationEnd(animation: Animator) = Unit
  override fun onAnimationCancel(animation: Animator) = Unit

  override fun onAnimationRepeat(animation: Animator) {
    animationConsumer.accept(animation)
  }
}
