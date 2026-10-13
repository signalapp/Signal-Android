/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.getWindowBreakpoint
import org.signal.core.ui.isWidthExpanded
import org.signal.core.ui.rememberIsSplitPane

private val MEDIUM_CONTENT_CORNERS = 18.dp
private val EXTENDED_CONTENT_CORNERS = 14.dp
private val EXPANDED_LIST_PANE_WIDTH = 416.dp

/**
 * The baseline geometry of a list/detail window: the values every Signal list/detail screen starts from,
 * and which you hand to [rememberListDetailPaneLayout].
 *
 * A single pane gets square corners and no padding, since there is no second pane to separate it from.
 *
 * @property shape the shape both panes are clipped to.
 * @property navigationBarShape the shape a navigation bar sitting at the bottom of the list pane is clipped
 *   to, which is [shape] with its top corners squared off.
 * @property partitionWidth the gap between the two panes, where the drag handle sits.
 * @property listPaddingStart padding between the window's start edge and the list pane.
 * @property detailPaddingEnd padding between the detail pane and the window's end edge.
 */
@Immutable
data class ListDetailPaneMetrics(
  val shape: Shape,
  val navigationBarShape: Shape,
  val partitionWidth: Dp,
  val listPaddingStart: Dp,
  val detailPaddingEnd: Dp
) {
  private val extraPadding: Dp = partitionWidth + listPaddingStart + detailPaddingEnd

  /**
   * The list pane's width while both panes are visible, which is the [PaneAnchorWidths.split] anchor.
   *
   * A window wide enough gives the list a fixed width and lets the detail take the rest; a narrower one
   * splits what is left over evenly. A single pane fills the window.
   *
   * @param maxWidth the width available to both panes, from the `BoxWithConstraints` around them.
   */
  @Composable
  fun rememberSplitListPaneWidth(maxWidth: Dp): Dp {
    val isSplitPane = LocalResources.current.rememberIsSplitPane()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return remember(maxWidth, windowSizeClass, isSplitPane) {
      when {
        !isSplitPane -> maxWidth
        windowSizeClass.isWidthExpanded -> EXPANDED_LIST_PANE_WIDTH
        else -> (maxWidth - extraPadding) / 2f
      }
    }
  }
}

/**
 * The baseline metrics for the current window.
 *
 * @param listPaddingStart overrides the padding at the start of the list pane, for a caller that needs to
 *   inset the list itself. Ignored in a single-pane window, which has no padding.
 */
@Composable
fun rememberListDetailPaneMetrics(listPaddingStart: Dp = 0.dp): ListDetailPaneMetrics {
  val resources = LocalResources.current
  val breakpoint = resources.getWindowBreakpoint()
  val isSplitPane = resources.rememberIsSplitPane()

  return remember(breakpoint, isSplitPane, listPaddingStart) {
    val corners = if (breakpoint is WindowBreakpoint.Large) EXTENDED_CONTENT_CORNERS else MEDIUM_CONTENT_CORNERS

    if (!isSplitPane) {
      ListDetailPaneMetrics(
        shape = RectangleShape,
        navigationBarShape = RectangleShape,
        partitionWidth = 0.dp,
        listPaddingStart = 0.dp,
        detailPaddingEnd = 0.dp
      )
    } else {
      ListDetailPaneMetrics(
        shape = RoundedCornerShape(corners),
        navigationBarShape = RoundedCornerShape(0.dp, 0.dp, corners, corners),
        partitionWidth = if (breakpoint is WindowBreakpoint.Large) 24.dp else 13.dp,
        listPaddingStart = listPaddingStart,
        detailPaddingEnd = if (breakpoint is WindowBreakpoint.Large) 24.dp else 12.dp
      )
    }
  }
}
