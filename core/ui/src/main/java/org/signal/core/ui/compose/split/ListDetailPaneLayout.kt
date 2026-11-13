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
  internal val minListContentWidth: Dp,
  internal val minDetailContentWidth: Dp
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
 * @param isResizable whether the divider can be dragged. When false the panes stay wherever [paneAnchor] puts them.
 */
@Composable
fun rememberListDetailPaneLayout(
  paneAnchor: PaneAnchor,
  maxWidth: Dp,
  onAnchorSelected: (PaneAnchor) -> Unit,
  metrics: ListDetailPaneMetrics = rememberListDetailPaneMetrics(),
  collapsedListWidth: Dp = 0.dp,
  isResizable: Boolean = true
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

  return remember(expansion, metrics, isSplitPane, splitWidth, maxWidth, isResizable) {
    ListDetailPaneLayout(
      expansion = expansion,
      partitionWidth = metrics.partitionWidth,
      listPaddingStart = metrics.listPaddingStart,
      detailPaddingEnd = metrics.detailPaddingEnd,
      shape = metrics.shape,
      hasDragHandle = isSplitPane && isResizable,
      minListContentWidth = if (isSplitPane) metrics.listContentWidthAtSplit(splitWidth) else splitWidth,
      minDetailContentWidth = if (isSplitPane) metrics.detailWidthAtSplit(maxWidth, splitWidth) else splitWidth
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

/**
 * The chrome for the list pane, in two forms. [movable] carries its state from one list to the next, and so can only be
 * placed once: it goes to the list in the scene being navigated to. A different list still on screen in the scene
 * being navigated away from gets its own [chrome] for as long as it animates out.
 */
internal class ListPaneChromeHost(
  val chrome: ListPaneChrome,
  val movable: ListPaneChrome
)

internal val LocalListPaneChrome = compositionLocalOf<ListPaneChromeHost?> { null }

/**
 * Fills the detail pane when there is no detail content to show. Defaults to nothing, leaving the pane's
 * own background.
 */
internal val LocalEmptyDetailContent = compositionLocalOf<@Composable () -> Unit> { {} }

/**
 * Whether content is displayed in the detail pane of a split, beside the list. False for a single pane, including
 * detail content filling a narrow window.
 */
val LocalIsInDetailPane = compositionLocalOf { false }

/**
 * Whether content is the bottom of the detail stack beside the list, where back has nothing in the pane to return to.
 */
val LocalIsDetailPaneRoot = compositionLocalOf { false }

/**
 * The content key of the bottom of the detail stack beside the list, or null outside a split.
 */
internal val LocalDetailPaneRootContentKey = compositionLocalOf<Any?> { null }

/**
 * The list pane's content width while both panes are visible: the list's [splitListWidth] less the padding at its start,
 * such as the inset the main screen's search bar needs.
 */
internal fun ListDetailPaneMetrics.listContentWidthAtSplit(splitListWidth: Dp): Dp {
  return (splitListWidth - listPaddingStart).coerceAtLeast(0.dp)
}

/**
 * The detail pane's width while both panes are visible: whatever the list, the gap between them and the detail's end
 * padding leave of [maxWidth]. This is narrower than the list on a window that is only just wide enough for the list's
 * fixed width, such as an open foldable.
 */
internal fun ListDetailPaneMetrics.detailWidthAtSplit(maxWidth: Dp, splitListWidth: Dp): Dp {
  return (maxWidth - splitListWidth - partitionWidth - detailPaddingEnd).coerceAtLeast(0.dp)
}
