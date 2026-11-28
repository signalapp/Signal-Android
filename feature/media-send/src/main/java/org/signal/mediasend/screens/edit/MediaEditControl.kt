/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.signal.core.ui.compose.Chrome
import org.signal.mediasend.screens.MediaSendMetrics

/** A [Chrome.Control] on the edit screen, carrying the transitions the rest of the screen animates with. */
@Composable
internal fun MediaEditControl(
  faded: Boolean,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  enter: EnterTransition = MediaSendMetrics.ControlEnterTransition,
  exit: ExitTransition = MediaSendMetrics.ControlExitTransition,
  content: @Composable () -> Unit
) {
  Chrome.Control(
    modifier = modifier,
    visible = visible,
    faded = faded,
    enter = enter,
    exit = exit,
    content = content
  )
}
