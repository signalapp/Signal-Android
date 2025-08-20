/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.window.WindowSizeClass

private val MEDIUM_CONTENT_CORNERS = 18.dp
private val EXTENDED_CONTENT_CORNERS = 14.dp

/**
 * Describes metrics for the content layout (list and detail) of the main screen.
 *
 * @param shape The clipping shape of each of the list and detail fragments
 * @param navigationBarShape The clipping shape applied to the navigation bar, if present.
 * @param partitionWidth The width of the divider between list and detail
 * @param listPaddingStart The padding between the list pane and the navigation rail
 * @param detailPaddingEnd The padding at the end of the detail pane
 */
@Immutable
data class MainContentLayoutData(
  val shape: Shape,
  val navigationBarShape: Shape,
  val partitionWidth: Dp,
  val listPaddingStart: Dp,
  val detailPaddingEnd: Dp
) {
  private val extraPadding: Dp = partitionWidth + listPaddingStart + detailPaddingEnd

  /**
   * Whether or not the WindowSizeClass supports drag handles.
   */
  @Composable
  fun hasDragHandle(): Boolean {
    return WindowSizeClass.rememberWindowSizeClass().isExtended()
  }

  /**
   * Calculates the default preferred width
   */
  @Composable
  fun rememberDefaultPanePreferredWidth(maxWidth: Dp): Dp {
    val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

    return remember(maxWidth, windowSizeClass) {
      when {
        !windowSizeClass.isSplitPane() -> maxWidth
        windowSizeClass.isExtended() -> 416.dp
        else -> (maxWidth - extraPadding) / 2f
      }
    }
  }

  companion object {
    /**
     * Uses the WindowSizeClass to build out a MainContentLayoutData.
     */
    @Composable
    fun rememberContentLayoutData(): MainContentLayoutData {
      val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

      return remember(windowSizeClass) {
        MainContentLayoutData(
          shape = when {
            !windowSizeClass.isSplitPane() -> RectangleShape
            windowSizeClass.isExtended() -> RoundedCornerShape(EXTENDED_CONTENT_CORNERS)
            else -> RoundedCornerShape(MEDIUM_CONTENT_CORNERS)
          },
          navigationBarShape = when {
            !windowSizeClass.isSplitPane() -> RectangleShape
            windowSizeClass.isExtended() -> RoundedCornerShape(0.dp, 0.dp, EXTENDED_CONTENT_CORNERS, EXTENDED_CONTENT_CORNERS)
            else -> RoundedCornerShape(0.dp, 0.dp, MEDIUM_CONTENT_CORNERS, MEDIUM_CONTENT_CORNERS)
          },
          partitionWidth = when {
            !windowSizeClass.isSplitPane() -> 0.dp
            windowSizeClass.isExtended() -> 16.dp
            else -> 13.dp
          },
          listPaddingStart = when {
            !windowSizeClass.isSplitPane() -> 0.dp
            windowSizeClass.isExtended() -> 16.dp
            else -> 12.dp
          },
          detailPaddingEnd = when {
            !windowSizeClass.isSplitPane() -> 0.dp
            windowSizeClass.isExtended() -> 24.dp
            else -> 12.dp
          }
        )
      }
    }
  }
}
