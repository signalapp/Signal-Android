/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/** The controls laid over a full-bleed surface, such as a camera preview or a photo being edited. */
object Chrome {

  /**
   * A control on such a surface, and the two ways it gets out of the user's way. Going not-[visible] gives up its
   * layout space, letting the rest of the stack settle into it, while [faded] holds onto the space -- releasing it
   * mid-gesture would move whatever the user is dragging out from under their finger.
   */
  @Composable
  fun Control(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    faded: Boolean = false,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable () -> Unit
  ) {
    val alpha by animateFloatAsState(targetValue = if (faded) 0f else 1f, label = "ChromeControlAlpha")

    AnimatedVisibility(
      visible = visible,
      enter = enter,
      exit = exit,
      modifier = modifier.alpha(alpha)
    ) {
      content()
    }
  }
}
