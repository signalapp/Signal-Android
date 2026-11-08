/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.runtime.Composable

/**
 * Where a window's top-level navigation is placed: along the start edge, or across the bottom.
 */
enum class NavigationType {
  RAIL,
  BAR;

  companion object {
    @Composable
    fun rememberNavigationType(): NavigationType = rememberWindowBreakpoint().navigationType
  }
}

/**
 * A window gets a rail once it is wide enough for one to sit beside the content rather than eat into it.
 */
val WindowBreakpoint.navigationType: NavigationType
  get() = when (this) {
    is WindowBreakpoint.Small -> NavigationType.BAR
    is WindowBreakpoint.Medium -> if (isWidthExpanded) NavigationType.RAIL else NavigationType.BAR
    is WindowBreakpoint.Large -> NavigationType.RAIL
  }
