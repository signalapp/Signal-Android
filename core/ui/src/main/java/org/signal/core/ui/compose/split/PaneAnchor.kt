/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * How a split-pane window divides the list and detail panes.
 */
enum class PaneAnchor {
  /** The detail pane fills the window, with the list pushed off the start edge. */
  DETAIL_ONLY,

  /** Both panes are visible. */
  SPLIT,

  /** The list fills the window, with the detail pane pushed off the end edge. */
  LIST_ONLY
}

/**
 * The width the list pane takes at each [PaneAnchor].
 *
 * @property detailOnly width left to the list once the detail fills the window. Not necessarily zero: a
 *   navigation rail living inside the list pane stays on screen.
 * @property split width of the list pane while both panes are visible.
 * @property listOnly width the list grows to once it fills the window, which leaves room for whatever
 *   padding the detail pane's edge needs.
 */
data class PaneAnchorWidths(
  val detailOnly: Dp,
  val split: Dp,
  val listOnly: Dp
) {
  internal fun toOffsets(density: Density): Map<PaneAnchor, Float> {
    return with(density) {
      mapOf(
        PaneAnchor.DETAIL_ONLY to detailOnly.toPx(),
        PaneAnchor.SPLIT to split.toPx(),
        PaneAnchor.LIST_ONLY to listOnly.toPx()
      )
    }
  }
}
