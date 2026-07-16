/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay

/**
 * A collection of transition specs for setting up nav3 navigation.
 */
object TransitionSpecs {

  interface Transition {
    companion object {
      val NONE: ContentTransform = EnterTransition.None togetherWith ExitTransition.None
    }

    val transitionSpec: ContentTransform get() = NONE
    val popTransitionSpec: ContentTransform get() = NONE
    val predictivePopTransitionSpec: ContentTransform get() = NONE

    val metadata: Map<String, Any> get() = metadata {
      put(NavDisplay.TransitionKey) {
        transitionSpec
      }
      put(NavDisplay.PopTransitionKey) {
        popTransitionSpec
      }
      put(NavDisplay.PredictivePopTransitionKey) {
        predictivePopTransitionSpec
      }
    }
  }

  /**
   * No enter/exit animation.
   */
  object None : Transition {
    override val transitionSpec: ContentTransform = Transition.NONE
    override val popTransitionSpec: ContentTransform = Transition.NONE
    override val predictivePopTransitionSpec: ContentTransform = Transition.NONE
  }

  /**
   * Screens slide in from the right and slide out from the left.
   */
  object HorizontalSlide : Transition {
    private const val DURATION = 200

    override val transitionSpec: ContentTransform =
      (
        slideInHorizontally(
          initialOffsetX = { it },
          animationSpec = tween(DURATION)
        ) + fadeIn(animationSpec = tween(DURATION))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(DURATION)
          ) + fadeOut(animationSpec = tween(DURATION))
          )

    override val popTransitionSpec: ContentTransform =
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(DURATION)
        ) + fadeIn(animationSpec = tween(DURATION))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(DURATION)
          ) + fadeOut(animationSpec = tween(DURATION))
          )

    override val predictivePopTransitionSpec: ContentTransform =
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(DURATION)
        ) + fadeIn(animationSpec = tween(DURATION))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(DURATION)
          ) + fadeOut(animationSpec = tween(DURATION))
          )
  }

  /**
   * Screens slide in from the bottom and slide out to the bottom, like a sheet.
   */
  object VerticalSlide : Transition {
    private const val DURATION = 300

    override val transitionSpec: ContentTransform =
      slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(DURATION)
      ) + fadeIn(animationSpec = tween(DURATION)) togetherWith
        fadeOut(animationSpec = tween(DURATION))

    override val popTransitionSpec: ContentTransform =
      fadeIn(animationSpec = tween(DURATION)) togetherWith
        slideOutVertically(
          targetOffsetY = { it },
          animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))

    override val predictivePopTransitionSpec: ContentTransform =
      fadeIn(animationSpec = tween(DURATION)) togetherWith
        slideOutVertically(
          targetOffsetY = { it },
          animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))
  }
}
