/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * How one control gives way to another in place: whichever is arriving scales up as it fades in, and whichever is
 * leaving scales down as it fades out.
 *
 * Shared so that the corner beside the capture button swapping the lock for the pause, and the pause swapping its own
 * icon for the play, read as one movement rather than two that nearly match.
 */
internal object CameraHudMotion {

  const val SWAP_DURATION_MS = 200
  const val SWAP_SCALE = 0.92f

  val swap: ContentTransform
    get() {
      val spec = tween<Float>(SWAP_DURATION_MS)

      return (scaleIn(initialScale = SWAP_SCALE, animationSpec = spec) + fadeIn(animationSpec = spec))
        .togetherWith(scaleOut(targetScale = SWAP_SCALE, animationSpec = spec) + fadeOut(animationSpec = spec))
    }
}
