/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.rememberIsSplitPane

/**
 * Geometry for the list/detail split, provided to [ListDetailScene] through [LocalListDetailPaneLayout].
 */
@Stable
class ListDetailPaneLayout internal constructor(
  internal val expansion: PaneExpansionState,
  internal val partitionWidth: Dp,
  internal val listPaddingStart: Dp,
  internal val detailPaddingEnd: Dp,
  internal val shape: Shape,
  internal val hasDragHandle: Boolean,
  internal val minPaneContentWidth: Dp
)

/**
 * The current split geometry. A null layout means the display has no geometry to split with, and the
 * scene falls back to showing the list on its own.
 */
internal val LocalListDetailPaneLayout = compositionLocalOf<ListDetailPaneLayout?> { null }

/**
 * Builds the geometry for a list/detail split and keeps it following [paneAnchor], reporting a dragged
 * divider back through [onAnchorSelected].
 *
 * Everything but the anchors comes from [metrics], so the usual call passes a [paneAnchor], a [maxWidth]
 * and nothing else. Override [metrics] when a screen needs to depart from the baseline.
 *
 * @param paneAnchor the anchor the divider settles at.
 * @param maxWidth the width available to both panes, from the `BoxWithConstraints` around them.
 * @param onAnchorSelected the anchor a drag settled on, or that an accessibility action asked for.
 *   Note: The caller is expected to feed it back in through [paneAnchor], which is what
 *   actually moves the divider.
 * @param metrics the baseline geometry, which decides the widths, the gutter and the corners.
 * @param collapsedListWidth what is left of the list pane once the detail fills the window. Zero unless
 *   something inside the list stays on screen, such as a navigation rail.
 */
@Composable
fun rememberListDetailPaneLayout(
  paneAnchor: PaneAnchor,
  maxWidth: Dp,
  onAnchorSelected: (PaneAnchor) -> Unit,
  metrics: ListDetailPaneMetrics = rememberListDetailPaneMetrics(),
  collapsedListWidth: Dp = 0.dp
): ListDetailPaneLayout {
  val isSplitPane = LocalResources.current.rememberIsSplitPane()
  val splitWidth = metrics.rememberSplitListPaneWidth(maxWidth)

  val anchorWidths = remember(collapsedListWidth, splitWidth, maxWidth, metrics) {
    PaneAnchorWidths(
      detailOnly = collapsedListWidth + metrics.listPaddingStart,
      split = splitWidth,
      listOnly = maxWidth - metrics.detailPaddingEnd
    )
  }

  val density = LocalDensity.current
  val anchorOffsets = remember(density, anchorWidths) { anchorWidths.toOffsets(density) }

  val expansion = rememberPaneExpansionState(
    initialOffsetPx = anchorOffsets.getValue(paneAnchor),
    onAnchorSelected = onAnchorSelected
  )

  LaunchedEffect(anchorOffsets, paneAnchor) {
    expansion.updateAnchors(anchorOffsets)
    expansion.goTo(paneAnchor)
  }

  return remember(expansion, metrics, isSplitPane, splitWidth) {
    ListDetailPaneLayout(
      expansion = expansion,
      partitionWidth = metrics.partitionWidth,
      listPaddingStart = metrics.listPaddingStart,
      detailPaddingEnd = metrics.detailPaddingEnd,
      shape = metrics.shape,
      hasDragHandle = isSplitPane,
      minPaneContentWidth = splitWidth
    )
  }
}

/**
 * Wraps list content in the static chrome that belongs to the list pane: navigation rail or bar, toolbar, and
 * anything layered over them.
 *
 * Supplied through a composition local so that a scene can place it *around* the list entry, keeping one
 * chrome instance alive while the list content swaps underneath.
 */
typealias ListPaneChrome = @Composable (content: @Composable () -> Unit) -> Unit

internal val LocalListPaneChrome = compositionLocalOf<ListPaneChrome?> { null }

/**
 * Fills the detail pane when there is no detail content to show. Defaults to nothing, leaving the pane's
 * own background.
 */
internal val LocalEmptyDetailContent = compositionLocalOf<@Composable () -> Unit> { {} }
