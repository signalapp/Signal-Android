/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.animation

import android.animation.Animator

abstract class AnimationCompleteListener : Animator.AnimatorListener {
  abstract override fun onAnimationEnd(animation: Animator)

  override fun onAnimationStart(animation: Animator) = Unit
  override fun onAnimationCancel(animation: Animator) = Unit
  override fun onAnimationRepeat(animation: Animator) = Unit
}
