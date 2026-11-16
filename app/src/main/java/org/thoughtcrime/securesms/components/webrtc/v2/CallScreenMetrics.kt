/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

@Stable
class CallScreenMetrics @RememberInComposition constructor(
  private val windowSizeClass: WindowSizeClass
) {

  companion object {
    val OverflowParticipantRendererCornerSize = 24.dp
    val ExpandedRendererCornerSize = 28.dp
    val FocusedRendererCornerSize = 32.dp

    /**
     * Shape of self renderer when in large group calls.
     */
    val OverflowParticipantRendererShape = RoundedCornerShape(OverflowParticipantRendererCornerSize)

    /**
     * Maximum width of the bottom sheet and related UI bars on the call screen.
     */
    val SheetMaxWidth = 540.dp
  }

  /**
   * Represents the size of the renderer for the participant overflow and the mini self-pip.
   */
  val overflowParticipantRendererSize: Dp = forWindowSizeClass(
    compact = 96.dp,
    medium = 116.dp
  )

  val overflowParticipantRendererAvatarSize: Dp = forWindowSizeClass(
    compact = 48.dp,
    medium = 56.dp
  )

  /**
   * Number of other participants at which the self pip shrinks to the overflow renderer size.
   */
  val selfPipShrinkThreshold: Int = forWindowSizeClass(
    compact = 5,
    medium = 9
  )

  /** Inset of the overflow strip from the safe area, along and at the end of the strip. */
  val overflowStripEdgeInset: Dp = forWindowSizeClass(
    compact = 16.dp,
    medium = 24.dp
  )

  /** Gap between cells in the overflow strip. */
  val overflowStripItemSpacing: Dp = forWindowSizeClass(
    compact = 10.dp,
    medium = 12.dp
  )

  /**
   * Extra gap between the grid and a horizontal overflow strip, making up the difference where CallGrid's
   * vertical outer padding is zero. A vertical strip needs none: horizontal outer padding never is.
   */
  val overflowStripGridGap: Dp = forWindowSizeClass(
    compact = 0.dp,
    medium = 16.dp
  )

  val overflowInfoIconSize: Dp = forWindowSizeClass(
    compact = 24.dp,
    medium = 28.dp
  )

  private val normalRendererDpWidth: Dp = forWindowSizeClass(
    compact = 96.dp,
    medium = 132.dp
  )

  private val normalRendererDpHeight: Dp = forWindowSizeClass(
    compact = 171.dp,
    medium = 235.dp
  )

  private val expandedRendererDpWidth: Dp = forWindowSizeClass(
    compact = 148.dp,
    medium = 180.dp
  )

  private val expandedRendererDpHeight: Dp = forWindowSizeClass(
    compact = 263.dp,
    medium = 321.dp
  )

  /**
   * Size of self renderer when in large group calls
   */
  val overflowParticipantRendererDpSize get() = DpSize(overflowParticipantRendererSize, overflowParticipantRendererSize)

  /**
   * Size of self renderer when in small group calls and 1:1 calls
   */
  val normalRendererDpSize get() = DpSize(normalRendererDpWidth, normalRendererDpHeight)

  /**
   * Size of self renderer after clicking on it to expand
   */
  val expandedRendererDpSize get() = DpSize(expandedRendererDpWidth, expandedRendererDpHeight)

  private fun <T> forWindowSizeClass(
    compact: T,
    medium: T = compact,
    expanded: T = medium
  ): T {
    return if (windowSizeClass.isAtLeastBreakpoint(
        widthDpBreakpoint = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND
      )
    ) {
      expanded
    } else if (windowSizeClass.isAtLeastBreakpoint(
        widthDpBreakpoint = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
      )
    ) {
      medium
    } else {
      compact
    }
  }
}

/**
 * Whether the call renders edge to edge: a single remote participant on a compact window, where the grid is
 * full bleed and the system bars hide with the controls. The grid, call screen and activity must agree.
 */
@Composable
fun rememberIsFullBleedCall(remoteParticipantCount: Int): Boolean {
  val callGridStrategy = rememberCallGridStrategy()
  val isCompact = callGridStrategy is CallGridStrategy.SmallPortrait || callGridStrategy is CallGridStrategy.SmallLandscape

  return isCompact && remoteParticipantCount == 1
}

@Composable
fun rememberCallScreenMetrics(): CallScreenMetrics {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass) { CallScreenMetrics(windowSizeClass) }
}
