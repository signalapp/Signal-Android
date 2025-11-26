/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp

/**
 * Default animation settings for app-scaffold animations.
 */
object AppScaffoldAnimationDefaults {
  val TweenEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
  val InitAnimationOffset = 48.dp

  fun <T> tween() = tween<T>(durationMillis = 200, easing = TweenEasing)
}

data class AppScaffoldAnimationState(
  private val alpha: State<Float> = mutableStateOf(1f),
  private val scale: State<Float> = mutableStateOf(1f),
  val scaleMinimum: Float = 0f,
  private val offset: State<Dp> = mutableStateOf(0.dp),
  private val corners: State<Dp> = mutableStateOf(0.dp),
  val cornersMaximum: Dp = 1000.dp,
  private val parentOverlayAlpha: State<Float> = mutableStateOf(0f)
) {

  private val unclampedScale by scale
  private val unclampedCorners by corners

  val contentAlpha by alpha
  val contentScale by derivedStateOf { unclampedScale.coerceAtLeast(scaleMinimum) }
  val contentOffset by offset
  val contentCorners by derivedStateOf { unclampedCorners.coerceAtMost(cornersMaximum) }

  fun ContentDrawScope.applyParentValues() {
    drawContent()

    drawRect(Color(0f, 0f, 0f, parentOverlayAlpha.value))
  }

  fun GraphicsLayerScope.applyChildValues() {
    this.alpha = contentAlpha
    this.scaleX = contentScale
    this.scaleY = contentScale
    this.translationX = contentOffset.toPx()
    this.translationY = 0f
    this.clip = true
    this.shape = RoundedCornerShape(contentCorners)
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
      alpha = mutableStateOf(1f)
    )
  }

  private var latestListSeekState: AppScaffoldAnimationState = EMPTY_STATE
  private var latestDetailSeekState: AppScaffoldAnimationState = EMPTY_STATE

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
