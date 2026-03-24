/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/**
 * A collection of [TransitionSpecs] for setting up nav3 navigation.
 */
object TransitionSpecs {

  /**
   * Screens slide in from the right and slide out from the left.
   */
  object HorizontalSlide {
    private const val DURATION = 200

    val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
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
    }

    val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
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

    val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
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
  }

  /**
   * Screens slide in from the bottom and slide out to the bottom, like a sheet.
   */
  object VerticalSlide {
    private const val DURATION = 300

    val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
      slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(DURATION)
      ) + fadeIn(animationSpec = tween(DURATION)) togetherWith
        fadeOut(animationSpec = tween(DURATION))
    }

    val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
      fadeIn(animationSpec = tween(DURATION)) togetherWith
        slideOutVertically(
          targetOffsetY = { it },
          animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))
    }

    val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
      fadeIn(animationSpec = tween(DURATION)) togetherWith
        slideOutVertically(
          targetOffsetY = { it },
          animationSpec = tween(DURATION)
        ) + fadeOut(animationSpec = tween(DURATION))
    }
  }
}
