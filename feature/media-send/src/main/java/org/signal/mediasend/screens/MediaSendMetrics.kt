/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object MediaSendMetrics {
  val SelectedMediaPreviewSize = DpSize(44.dp, 44.dp)
  val SelectedMediaPreviewShape = RoundedCornerShape(8.dp)

  val MediaProjectionGutter = 16.dp

  val ControlEnterTransition: EnterTransition = fadeIn()
  val ControlExitTransition: ExitTransition = fadeOut()

  val BottomBarMaxWidth: Dp = 624.dp

  /**
   * For the bottom-most control of a bottom-aligned stack: the control slides off the bottom edge while giving up its
   * layout space at the same rate, so everything above it slides along with it as a unit.
   */
  val SlidingControlEnterTransition: EnterTransition = ControlEnterTransition + expandVertically(expandFrom = Alignment.Top, clip = false)
  val SlidingControlExitTransition: ExitTransition = ControlExitTransition + shrinkVertically(shrinkTowards = Alignment.Top, clip = false)
}
