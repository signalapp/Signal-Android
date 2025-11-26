/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object CallScreenMetrics {
  val SmallRendererSize = 90.dp
  val SmallRendererCornerSize = 24.dp
  val ExpandedRendererCornerSize = 28.dp
  val FocusedRendererCornerSize = 32.dp

  /**
   * Shape of self renderer when in large group calls.
   */
  val SmallRendererShape = RoundedCornerShape(SmallRendererCornerSize)

  /**
   * Size of self renderer when in large group calls
   */
  val SmallRendererDpSize = DpSize(SmallRendererSize, SmallRendererSize)

  /**
   * Size of self renderer when in small group calls and 1:1 calls
   */
  val NormalRendererDpSize = DpSize(90.dp, 160.dp)

  /**
   * Size of self renderer after clicking on it to expand
   */
  val ExpandedRendererDpSize = DpSize(170.dp, 300.dp)
}
