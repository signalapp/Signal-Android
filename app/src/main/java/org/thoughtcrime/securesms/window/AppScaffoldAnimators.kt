/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.animateDp(
  transitionSpec: @Composable Transition.Segment<*>.() -> FiniteAnimationSpec<Dp> = { AppScaffoldAnimationDefaults.tween() },
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
  transitionSpec: @Composable Transition.Segment<*>.() -> FiniteAnimationSpec<Float> = { AppScaffoldAnimationDefaults.tween() },
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
  val offset = animateDp(
    targetWhenHiding = {
      -AppScaffoldAnimationDefaults.InitAnimationOffset
    },
    targetWhenShowing = {
      0.dp
    }
  )

  val alpha = animateFloat {
    1f
  }

  return remember {
    AppScaffoldAnimationState(
      alpha = alpha,
      offset = offset
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultListSeekAnimationState(): AppScaffoldAnimationState {
  val scale = animateFloat(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 0.5f },
    targetWhenHiding = { 1f }
  )

  val offset = animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenHiding = { -(88.dp) },
    targetWhenShowing = { 0.dp }
  )

  return remember {
    AppScaffoldAnimationState(
      offset = offset,
      scale = scale,
      scaleMinimum = 0.9f,
      parentOverlayAlpha = mutableStateOf(0.2f)
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultListReleaseAnimationState(from: AppScaffoldAnimationState): AppScaffoldAnimationState {
  val initialScale = remember { from.contentScale }
  val initialOffset = remember { from.contentOffset }

  val scale = animateFloat(
    targetWhenHiding = { initialScale },
    targetWhenShowing = { 1f }
  )

  val offset = animateDp(
    targetWhenHiding = { initialOffset },
    targetWhenShowing = { 0.dp }
  )

  val alpha = animateFloat(
    targetWhenHiding = { 0.2f },
    targetWhenShowing = { 0f }
  )

  return remember {
    AppScaffoldAnimationState(
      scale = scale,
      scaleMinimum = from.scaleMinimum,
      offset = offset,
      parentOverlayAlpha = alpha
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailInitAnimationState(): AppScaffoldAnimationState {
  val offset = animateDp(
    targetWhenHiding = {
      AppScaffoldAnimationDefaults.InitAnimationOffset
    },
    targetWhenShowing = {
      0.dp
    }
  )

  val alpha = animateFloat {
    1f
  }

  return remember {
    AppScaffoldAnimationState(
      alpha = alpha,
      offset = offset
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailSeekAnimationState(): AppScaffoldAnimationState {
  val scale = animateFloat(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 1f },
    targetWhenHiding = { 0.5f }
  )

  val offset = animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    },
    targetWhenShowing = { 0.dp },
    targetWhenHiding = { 88.dp }
  )

  val roundedCorners = animateDp(
    transitionSpec = {
      appScaffoldSeekSpring()
    }
  ) { 1000.dp }

  return remember {
    AppScaffoldAnimationState(
      scale = scale,
      scaleMinimum = 0.9f,
      offset = offset,
      corners = roundedCorners,
      cornersMaximum = 42.dp
    )
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.defaultDetailReleaseAnimationState(from: AppScaffoldAnimationState): AppScaffoldAnimationState {
  val scale = remember { from.contentScale }
  val offset = remember { from.contentOffset }
  val corners = remember { from.contentCorners }

  val scaleState = remember { mutableStateOf(scale) }
  val offsetState = remember { mutableStateOf(offset) }
  val cornersState = remember { mutableStateOf(corners) }

  val alpha = animateFloat { 1f }

  return remember {
    AppScaffoldAnimationState(
      scale = scaleState,
      scaleMinimum = from.scaleMinimum,
      offset = offsetState,
      corners = cornersState,
      cornersMaximum = from.cornersMaximum,
      alpha = alpha
    )
  }
}
