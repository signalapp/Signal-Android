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
import androidx.compose.animation.slideOutHorizontally
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
    val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
      (
        slideInHorizontally(
          initialOffsetX = { it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
        )
    }

    val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
        )
    }

    val predictivePopTransitonSpec:  AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =  {
      (
        slideInHorizontally(
          initialOffsetX = { -it },
          animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200))
        ) togetherWith
        (
          slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(200)
          ) + fadeOut(animationSpec = tween(200))
        )
    }
  }
}