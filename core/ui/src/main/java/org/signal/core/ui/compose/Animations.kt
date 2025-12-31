/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Stable
import kotlin.time.Duration.Companion.milliseconds

object Animations {

  private val NAV_HOST_DEFAULT_ANIMATION_DURATION = 200.milliseconds

  @Stable
  fun <T> navHostDefaultAnimationSpec(): FiniteAnimationSpec<T> {
    return tween<T>(
      durationMillis = NAV_HOST_DEFAULT_ANIMATION_DURATION.inWholeMilliseconds.toInt()
    )
  }

  fun navHostSlideInTransition(initialOffsetX: (Int) -> Int): EnterTransition {
    return slideInHorizontally(
      animationSpec = navHostDefaultAnimationSpec(),
      initialOffsetX = initialOffsetX
    )
  }

  fun navHostSlideOutTransition(targetOffsetX: (Int) -> Int): ExitTransition {
    return slideOutHorizontally(
      animationSpec = navHostDefaultAnimationSpec(),
      targetOffsetX = targetOffsetX
    )
  }
}
