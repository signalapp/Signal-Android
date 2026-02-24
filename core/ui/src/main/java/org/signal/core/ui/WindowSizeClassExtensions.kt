/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.content.res.Resources
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

fun Resources.getWindowSizeClass(): WindowSizeClass {
  return WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
    widthDp = displayMetrics.widthPixels / displayMetrics.density,
    heightDp = displayMetrics.heightPixels / displayMetrics.density
  )
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
    widthDpBreakpoint = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
  )
}
