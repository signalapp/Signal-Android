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

/**
 * Describes metrics for the content layout (list and detail) of the main screen.
 *
 * @param shape The shape of each of the list and detail fragments
 * @param partitionWidth The width of the divider between list and detail
 * @param listPaddingStart The padding between the list pane and the navigation rail
 * @param detailPaddingEnd The padding at the end of the detail pane
 */
@Immutable
data class MainContentLayoutData(
  val shape: Shape,
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
        windowSizeClass.isCompact() -> maxWidth
        windowSizeClass.isMedium() -> (maxWidth - extraPadding) / 2f
        else -> 416.dp
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
            windowSizeClass.isCompact() -> RectangleShape
            windowSizeClass.isMedium() -> RoundedCornerShape(18.dp)
            else -> RoundedCornerShape(14.dp)
          },
          partitionWidth = when {
            windowSizeClass.isCompact() -> 0.dp
            windowSizeClass.isMedium() -> 13.dp
            else -> 16.dp
          },
          listPaddingStart = when {
            windowSizeClass.isCompact() -> 0.dp
            windowSizeClass.isMedium() -> 12.dp
            else -> 16.dp
          },
          detailPaddingEnd = when {
            windowSizeClass.isCompact() -> 0.dp
            windowSizeClass.isMedium() -> 12.dp
            else -> 24.dp
          }
        )
      }
    }
  }
}
