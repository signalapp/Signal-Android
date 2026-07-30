/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object MediaSendMetrics {
  val SelectedMediaPreviewSize = DpSize(44.dp, 44.dp)
  val SelectedMediaPreviewShape = RoundedCornerShape(8.dp)

  val ControlEnterTransition: EnterTransition = fadeIn()
  val ControlExitTransition: ExitTransition = fadeOut()
}
