/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldPaneScope.animateDp(
  targetWhenHiding: () -> Dp = { 0.dp },
  targetWhenShowing: () -> Dp
): State<Dp> {
  return scaffoldStateTransition.animateDp(
    transitionSpec = { tween(durationMillis = 200, easing = easing) }
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
  targetWhenHiding: () -> Float = { 0f },
  targetWhenShowing: () -> Float
): State<Float> {
  return scaffoldStateTransition.animateFloat(
    transitionSpec = { tween(durationMillis = 200, easing = easing) }
  ) {
    val isHiding = it[paneRole] == PaneAdaptedValue.Hidden

    if (isHiding) {
      targetWhenHiding()
    } else {
      targetWhenShowing()
    }
  }
}
