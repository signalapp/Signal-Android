/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

val WindowSizeClass.listPaneDefaultPreferredWidth: Dp get() = if (isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) 416.dp else 316.dp
val WindowSizeClass.horizontalPartitionDefaultSpacerSize: Dp get() = 12.dp
val WindowSizeClass.detailPaneMaxContentWidth: Dp get() = 624.dp

val WindowSizeClass.isWidthCompact
  get() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isHeightCompact
  get() = !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isWidthExpanded
  get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

fun Resources.getWindowSizeClass(): WindowSizeClass {
  return WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
    widthDp = displayMetrics.widthPixels / displayMetrics.density,
    heightDp = displayMetrics.heightPixels / displayMetrics.density
  )
}

@Composable
fun rememberWindowBreakpoint(): WindowBreakpoint {
  val resources = LocalResources.current
  val configuration = LocalConfiguration.current

  return remember(resources, configuration) {
    resources.getWindowBreakpoint()
  }
}

/**
 * Determines the device's form factor (PHONE, FOLDABLE, or TABLET) based on the current
 * [Resources] and window size class.
 *
 * This function uses several heuristics:
 * - Returns [WindowBreakpoint.SMALL] if the width or height is compact.
 * - Returns [WindowBreakpoint.LARGE] if the height is at least the expanded lower bound.
 * - Returns [WindowBreakpoint.MEDIUM] if the width is at least the medium lower bound.
 * - Otherwise, falls back to aspect ratio heuristics: wider (≥ 1.5) is [WindowBreakpoint.LARGE], else [WindowBreakpoint.MEDIUM].
 *
 * @return the inferred [WindowBreakpoint] for the current device.
 */
fun Resources.getWindowBreakpoint(): WindowBreakpoint {
  val windowSizeClass = getWindowSizeClass()

  if (windowSizeClass.isWidthCompact || windowSizeClass.isHeightCompact) {
    return WindowBreakpoint.SMALL
  }

  if (windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)) {
    return WindowBreakpoint.LARGE
  }

  val numerator = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val denominator = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val aspectRatio = numerator.toFloat() / denominator

  return if (aspectRatio >= 1.5f) {
    WindowBreakpoint.LARGE
  } else {
    WindowBreakpoint.MEDIUM
  }
}

/**
 * Indicates the general form factor of the device for responsive UI purposes.
 *
 * - [SMALL]: A window similar to a phone-sized device, typically with a compact width or height.
 * - [MEDIUM]: A window similar to a foldable or medium-size device, or a device which doesn't obviously fit into phone or tablet by heuristics.
 * - [LARGE]: A window similar to a large-screen tablet device, typically with an expanded height or wide aspect ratio.
 */
enum class WindowBreakpoint {
  SMALL,
  MEDIUM,
  LARGE
}

/**
 * Determines whether the UI should display in split-pane mode based on available screen space.
 */
@JvmOverloads
fun WindowSizeClass.isSplitPane(
  forceSplitPane: Boolean = CoreUiDependencies.forceSplitPane
): Boolean {
  if (forceSplitPane) {
    return true
  }

  return isAtLeastBreakpoint(
    widthDpBreakpoint = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
  )
}
