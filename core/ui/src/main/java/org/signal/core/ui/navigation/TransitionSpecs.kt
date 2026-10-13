/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay

/**
 * A collection of transition specs for setting up nav3 navigation.
 */
object TransitionSpecs {

  private const val PANE_SHIFT_DURATION = 200
  private val PANE_SHIFT_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)

  /**
   * The distance content travels during a [paneShift].
   */
  val PANE_SHIFT_OFFSET = 48.dp

  /**
   * The spec a [paneShift] animates with.
   */
  fun <T> paneShiftSpec(): FiniteAnimationSpec<T> = tween(durationMillis = PANE_SHIFT_DURATION, easing = PANE_SHIFT_EASING)

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
   * [paneShift] against the density and layout direction of wherever it is called, which is what a nav
   * display wants. Every display that uses this reads the same, whether it fills a pane or the window.
   *
   * @param pop reverses the direction, for navigating back.
   */
  @Composable
  fun paneShift(pop: Boolean = false): ContentTransform {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    return remember(density, layoutDirection, pop) { paneShift(density, layoutDirection, pop) }
  }

  /**
   * The short horizontal shift a pane makes as it enters or leaves: content moves [PANE_SHIFT_OFFSET]
   * rather than a whole width, cross-fading as it goes, with no scale.
   *
   * A function rather than a [Transition] value because the distance is fixed in dp, so it needs a
   * [Density].
   *
   * @param pop reverses the direction, for navigating back.
   */
  fun paneShift(density: Density, layoutDirection: LayoutDirection, pop: Boolean = false): ContentTransform {
    val offset = with(density) { PANE_SHIFT_OFFSET.roundToPx() }
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    val sign = if (pop) -1 else 1
    val slideSpec = paneShiftSpec<IntOffset>()
    val fadeSpec = paneShiftSpec<Float>()

    return slideInHorizontally(animationSpec = slideSpec) { offset * sign * direction } + fadeIn(animationSpec = fadeSpec) togetherWith
      slideOutHorizontally(animationSpec = slideSpec) { -offset * sign * direction } + fadeOut(animationSpec = fadeSpec)
  }

  /**
   * Suppresses only the *enter* transition, leaving pops to the display's defaults.
   *
   * For destinations that animate their own arrival and would otherwise be animated twice — a conversation
   * hands off from a bitmap of the list it came from — but which should still animate on the way out. Using
   * [None] here would suppress both directions, because a pop is resolved against the metadata of the
   * scene being left.
   */
  val suppressEnterMetadata: Map<String, Any> get() = metadata {
    put(NavDisplay.TransitionKey) {
      Transition.NONE
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
   * Screens fade in place, without any directional movement.
   */
  object Fade : Transition {
    private const val DURATION = 200

    override val transitionSpec: ContentTransform =
      (
        fadeIn(animationSpec = tween(DURATION))
        ) togetherWith
        (
          fadeOut(animationSpec = tween(DURATION))
          )

    override val popTransitionSpec: ContentTransform = transitionSpec

    override val predictivePopTransitionSpec: ContentTransform = transitionSpec
  }

  /**
   * Screens fade and zoom in place, without any directional movement.
   */
  object FadeScale : Transition {
    private const val DURATION = 200
    private const val SCALE = 0.92f

    override val transitionSpec: ContentTransform =
      (
        fadeIn(animationSpec = tween(DURATION)) +
          scaleIn(initialScale = SCALE, animationSpec = tween(DURATION))
        ) togetherWith
        (
          fadeOut(animationSpec = tween(DURATION)) +
            scaleOut(targetScale = SCALE, animationSpec = tween(DURATION))
          )

    override val popTransitionSpec: ContentTransform = transitionSpec

    override val predictivePopTransitionSpec: ContentTransform = transitionSpec
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
