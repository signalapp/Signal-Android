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

@Composable
fun rememberCallScreenMetrics(): CallScreenMetrics {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass) { CallScreenMetrics(windowSizeClass) }
}
