/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.window

import android.content.res.Resources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.ExperimentalWindowCoreApi
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import org.thoughtcrime.securesms.keyvalue.SignalStore

val WindowSizeClass.listPaneDefaultPreferredWidth: Dp get() = if (windowWidthSizeClass.isAtLeast(WindowWidthSizeClass.EXPANDED)) 416.dp else 316.dp
val WindowSizeClass.horizontalPartitionDefaultSpacerSize: Dp get() = 12.dp
val WindowSizeClass.detailPaneMaxContentWidth: Dp get() = 624.dp

fun WindowHeightSizeClass.isAtLeast(other: WindowHeightSizeClass): Boolean {
  return hashCode() >= other.hashCode()
}

fun WindowWidthSizeClass.isAtLeast(other: WindowWidthSizeClass): Boolean {
  return hashCode() >= other.hashCode()
}

@OptIn(ExperimentalWindowCoreApi::class)
fun Resources.getWindowSizeClass(): WindowSizeClass {
  return WindowSizeClass.compute(displayMetrics.widthPixels / displayMetrics.density, displayMetrics.heightPixels / displayMetrics.density)
}

/**
 * Determines whether the UI should display in split-pane mode based on available screen space.
 */
@JvmOverloads
fun WindowSizeClass.isSplitPane(
  forceSplitPane: Boolean = SignalStore.internal.forceSplitPane
): Boolean {
  if (forceSplitPane) {
    return true
  }

  return windowWidthSizeClass.isAtLeast(WindowWidthSizeClass.EXPANDED) &&
    windowHeightSizeClass.isAtLeast(WindowHeightSizeClass.MEDIUM)
}
