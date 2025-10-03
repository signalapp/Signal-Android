/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp

private const val SEEK_DAMPING_RATIO = Spring.DampingRatioNoBouncy
private const val SEEK_STIFFNESS = Spring.StiffnessMedium

/**
 * Default animation spec for back gesture seeking.
 */
fun <T> appScaffoldSeekSpring(): FiniteAnimationSpec<T> = spring(
  dampingRatio = SEEK_DAMPING_RATIO,
  stiffness = SEEK_STIFFNESS
)

private val easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.animateDp(
  transitionSpec: @Composable Transition.Segment<*>.() -> FiniteAnimationSpec<Dp> = { tween(durationMillis = 200, easing = easing) },
  targetWhenHiding: () -> Dp = { 0.dp },
  targetWhenShowing: () -> Dp
): State<Dp> {
  return scaffoldStateTransition.animateDp(
    transitionSpec = transitionSpec
  ) {
    val isHiding = it[paneRole] == PaneAdaptedValue.Hidden

    if (isHiding) {
      targetWhenHiding()
    } else {
      targetWhenShowing()
    }
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.animateFloat(
  transitionSpec: @Composable Transition.Segment<*>.() -> FiniteAnimationSpec<Float> = { tween(durationMillis = 200, easing = easing) },
  targetWhenHiding: () -> Float = { 0f },
  targetWhenShowing: () -> Float
): State<Float> {
  return scaffoldStateTransition.animateFloat(
    transitionSpec = transitionSpec
  ) {
    val isHiding = it[paneRole] == PaneAdaptedValue.Hidden

    if (isHiding) {
      targetWhenHiding()
    } else {
      targetWhenShowing()
    }
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultListInitAnimationState(): AppScaffoldAnimationState {
  val offset by animateDp(
    targetWhenHiding = {
      (-48).dp
    },
    targetWhenShowing = {
      0.dp
    }
  )

  val alpha by animateFloat {
    1f
  }

  return AppScaffoldAnimationState(
    AppScaffoldNavigator.NavigationState.INIT,
    alpha = alpha,
    offset = offset
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultListSeekAnimationState(): AppScaffoldAnimationState {
  val scale by animateFloat(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 0.5f },
    targetWhenHiding = { 1f }
  )

  val offset by animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenHiding = { -(88.dp) },
    targetWhenShowing = { 0.dp }
  )

  return AppScaffoldAnimationState(
    navigationState = AppScaffoldNavigator.NavigationState.SEEK,
    offset = offset,
    scale = scale.coerceAtLeast(0.9f),
    parentModifier = Modifier.drawWithContent {
      drawContent()

      drawRect(Color(0f, 0f, 0f, 0.2f))
    }
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultListReleaseAnimationState(from: AppScaffoldAnimationState): AppScaffoldAnimationState {
  val scale by animateFloat(
    targetWhenHiding = { from.scale },
    targetWhenShowing = { 1f }
  )

  val offset by animateDp(
    targetWhenHiding = { from.offset },
    targetWhenShowing = { 0.dp }
  )

  val alpha by animateFloat(
    targetWhenHiding = { 0.2f },
    targetWhenShowing = { 0f }
  )

  return AppScaffoldAnimationState(
    navigationState = AppScaffoldNavigator.NavigationState.RELEASE,
    scale = scale,
    offset = offset,
    parentModifier = Modifier.drawWithContent {
      drawContent()

      drawRect(Color(0f, 0f, 0f, alpha))
    }
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailInitAnimationState(): AppScaffoldAnimationState {
  val offset by animateDp(
    targetWhenHiding = {
      48.dp
    },
    targetWhenShowing = {
      0.dp
    }
  )

  val alpha by animateFloat {
    1f
  }

  return AppScaffoldAnimationState(
    navigationState = AppScaffoldNavigator.NavigationState.INIT,
    alpha = alpha,
    offset = offset
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailSeekAnimationState(): AppScaffoldAnimationState {
  val scale by animateFloat(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 1f },
    targetWhenHiding = { 0.5f }
  )

  val offset by animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 0.dp },
    targetWhenHiding = { 88.dp }
  )

  val roundedCorners by animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    }
  ) { 1000.dp }

  return AppScaffoldAnimationState(
    navigationState = AppScaffoldNavigator.NavigationState.SEEK,
    scale = scale.coerceAtLeast(0.9f),
    offset = offset,
    clipShape = RoundedCornerShape(roundedCorners.coerceAtMost(42.dp))
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailReleaseAnimationState(from: AppScaffoldAnimationState): AppScaffoldAnimationState {
  val alpha by animateFloat { 1f }

  return AppScaffoldAnimationState(
    navigationState = AppScaffoldNavigator.NavigationState.RELEASE,
    scale = from.scale,
    offset = from.offset,
    clipShape = from.clipShape,
    alpha = alpha
  )
}
