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

private const val TABLET_ASPECT_RATIO = 1.5f

val WindowSizeClass.listPaneDefaultPreferredWidth: Dp get() = if (isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) 416.dp else 316.dp
val WindowSizeClass.horizontalPartitionDefaultSpacerSize: Dp get() = 12.dp
val WindowSizeClass.detailPaneMaxContentWidth: Dp get() = 624.dp

val WindowSizeClass.isWidthCompact
  get() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isWidthExpanded
  get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

val WindowSizeClass.isHeightCompact
  get() = !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isHeightExpanded
  get() = isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)

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
 * Determines the device's form factor based on the current [Resources] and window size class.
 *
 * This function uses several heuristics:
 * - Returns [WindowBreakpoint.Small] if the window width or height is compact.
 * - Otherwise, falls back to window aspect ratio heuristics:
 *   aspect ratio < [TABLET_ASPECT_RATIO] is [WindowBreakpoint.Medium]
 *   aspect ratio >= [TABLET_ASPECT_RATIO] is [WindowBreakpoint.Large]
 *
 * @return the inferred [WindowBreakpoint] for the current device.
 */
fun Resources.getWindowBreakpoint(): WindowBreakpoint {
  val windowSizeClass = getWindowSizeClass()
  val isWidthExpanded = windowSizeClass.isWidthExpanded
  val isHeightExpanded = windowSizeClass.isHeightExpanded

  if (windowSizeClass.isWidthCompact || windowSizeClass.isHeightCompact) {
    return WindowBreakpoint.Small(isWidthExpanded, isHeightExpanded)
  }

  val numerator = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val denominator = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
  val aspectRatio = numerator.toFloat() / denominator

  return if (aspectRatio < TABLET_ASPECT_RATIO) {
    WindowBreakpoint.Medium(isWidthExpanded, isHeightExpanded)
  } else {
    WindowBreakpoint.Large(isWidthExpanded, isHeightExpanded)
  }
}

/**
 * Indicates the general form factor of the device for responsive UI purposes.
 *
 * - [Small]: A window with a compact width or height, typical of phone-sized devices.
 * - [Medium]: A non-compact window with a near-square aspect ratio (< [TABLET_ASPECT_RATIO]), typical of open foldable devices.
 * - [Large]: A window with an aspect ratio >= [TABLET_ASPECT_RATIO], typical of tablets.
 */
sealed interface WindowBreakpoint {
  /** True when the [WindowSizeClass] width is >= the expanded breakpoint (e.g., a tablet in landscape orientation) */
  val isWidthExpanded: Boolean

  /** True when the [WindowSizeClass] height is >= the expanded breakpoint (e.g., a tablet in landscape orientation) */
  val isHeightExpanded: Boolean

  data class Small(
    override val isWidthExpanded: Boolean,
    override val isHeightExpanded: Boolean
  ) : WindowBreakpoint

  data class Medium(
    override val isWidthExpanded: Boolean,
    override val isHeightExpanded: Boolean
  ) : WindowBreakpoint

  data class Large(
    override val isWidthExpanded: Boolean,
    override val isHeightExpanded: Boolean
  ) : WindowBreakpoint
}

@Composable
fun Resources.rememberIsSplitPane(
  forceSplitPane: Boolean = CoreUiDependencies.forceSplitPane
): Boolean {
  return remember(this, forceSplitPane) {
    isSplitPane(forceSplitPane)
  }
}

/**
 * Determines whether the UI should display in split-pane mode based on available screen space.
 */
@JvmOverloads
fun Resources.isSplitPane(
  forceSplitPane: Boolean = CoreUiDependencies.forceSplitPane
): Boolean {
  if (forceSplitPane) {
    return true
  }

  return when (val breakpoint = getWindowBreakpoint()) {
    is WindowBreakpoint.Small -> false
    is WindowBreakpoint.Medium -> true
    is WindowBreakpoint.Large -> breakpoint.isWidthExpanded
  }
}
