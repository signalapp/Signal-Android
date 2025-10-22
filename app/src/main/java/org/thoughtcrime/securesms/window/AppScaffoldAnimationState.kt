/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default animation settings for app-scaffold animations.
 */
object AppScaffoldAnimationDefaults {
  val TweenEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
  val InitAnimationOffset = 48.dp

  fun <T> tween() = tween<T>(durationMillis = 200, easing = TweenEasing)
}

/**
 * Produces modifier that can be composed into another modifier chain.
 * This object allows us to store "latest state" as we transition.
 *
 * @param parentModifier This modifier is applied to the [androidx.compose.material3.adaptive.layout.AnimatedPane] itself,
 *                       allowing additional customization like overlays without
 *                       the need for additional composables.
 */
data class AppScaffoldAnimationState(
  val navigationState: AppScaffoldNavigator.NavigationState,
  val alpha: Float = 1f,
  val scale: Float = 1f,
  val offset: Dp = 0.dp,
  val clipShape: Shape = RoundedCornerShape(0.dp),
  val parentModifier: Modifier = Modifier.Companion
) {
  fun toModifier(): Modifier {
    return Modifier
      .alpha(alpha)
      .scale(scale)
      .offset(offset)
      .clip(clipShape)
  }
}

/**
 * Allows for the customization of the AppScaffold Animators.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AppScaffoldAnimationStateFactory(
  val enabledStates: Set<AppScaffoldNavigator.NavigationState> = AppScaffoldNavigator.NavigationState.entries.toSet()
) {

  companion object {
    val Default = AppScaffoldAnimationStateFactory()

    private val EMPTY_STATE = AppScaffoldAnimationState(
      navigationState = AppScaffoldNavigator.NavigationState.ENTER,
      alpha = 1f
    )
  }

  private var latestListSeekState: AppScaffoldAnimationState = AppScaffoldAnimationState(AppScaffoldNavigator.NavigationState.SEEK)
  private var latestDetailSeekState: AppScaffoldAnimationState = AppScaffoldAnimationState(AppScaffoldNavigator.NavigationState.SEEK)

  @Composable
  fun ThreePaneScaffoldPaneScope.getListAnimationState(navigationState: AppScaffoldNavigator.NavigationState): AppScaffoldAnimationState {
    if (navigationState !in enabledStates) {
      return EMPTY_STATE
    }

    return when (navigationState) {
      AppScaffoldNavigator.NavigationState.ENTER -> defaultListInitAnimationState()
      AppScaffoldNavigator.NavigationState.EXIT -> defaultListInitAnimationState()
      AppScaffoldNavigator.NavigationState.SEEK -> defaultListSeekAnimationState().also {
        latestListSeekState = it
      }
      AppScaffoldNavigator.NavigationState.RELEASE -> defaultListReleaseAnimationState(latestListSeekState)
    }
  }

  @Composable
  fun ThreePaneScaffoldPaneScope.getDetailAnimationState(navigationState: AppScaffoldNavigator.NavigationState): AppScaffoldAnimationState {
    if (navigationState !in enabledStates) {
      return EMPTY_STATE
    }

    return when (navigationState) {
      AppScaffoldNavigator.NavigationState.ENTER -> defaultDetailInitAnimationState()
      AppScaffoldNavigator.NavigationState.EXIT -> defaultDetailInitAnimationState()
      AppScaffoldNavigator.NavigationState.SEEK -> defaultDetailSeekAnimationState().also {
        latestDetailSeekState = it
      }
      AppScaffoldNavigator.NavigationState.RELEASE -> defaultDetailReleaseAnimationState(latestDetailSeekState)
    }
  }
}
