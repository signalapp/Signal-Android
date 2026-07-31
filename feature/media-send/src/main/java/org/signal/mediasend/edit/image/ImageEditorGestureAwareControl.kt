/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.signal.mediasend.MediaSendMetrics
import org.signal.mediasend.edit.ImageController

/**
 * Hides or shows content based on whether the user is performing a gesture.
 */
@Composable
internal fun ImageEditorGestureAwareControl(
  imageEditorController: ImageController?,
  modifier: Modifier = Modifier,
  extraCheck: () -> Boolean = { true },
  content: @Composable () -> Unit
) {
  val isGestureActive = imageEditorController?.imageEditorState?.isGestureActive ?: false

  AnimatedVisibility(
    modifier = modifier,
    enter = MediaSendMetrics.ControlEnterTransition,
    exit = MediaSendMetrics.ControlExitTransition,
    visible = imageEditorController != null && imageEditorController.mode != ImageController.Mode.NONE && !isGestureActive && extraCheck()
  ) {
    content()
  }
}
