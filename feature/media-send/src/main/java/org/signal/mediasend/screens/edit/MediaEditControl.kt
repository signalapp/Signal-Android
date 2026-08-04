/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import org.signal.mediasend.screens.MediaSendMetrics

/**
 * A control on the edit screen, and the two ways it gets out of the user's way. Going not-[visible] gives up its layout
 * space, letting the rest of the stack settle into it, while [faded] holds onto the space -- releasing it mid-gesture
 * would move whatever the user is dragging out from under their finger.
 */
@Composable
internal fun MediaEditControl(
  faded: Boolean,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  enter: EnterTransition = MediaSendMetrics.ControlEnterTransition,
  exit: ExitTransition = MediaSendMetrics.ControlExitTransition,
  content: @Composable () -> Unit
) {
  val alpha by animateFloatAsState(targetValue = if (faded) 0f else 1f)

  AnimatedVisibility(
    visible = visible,
    enter = enter,
    exit = exit,
    modifier = modifier.alpha(alpha)
  ) {
    content()
  }
}
