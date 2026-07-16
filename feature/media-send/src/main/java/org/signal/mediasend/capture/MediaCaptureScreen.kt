/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.compose.Buttons
import org.signal.mediasend.MediaSendNavKey
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.R

/**
 * Screen that allows user to capture the media they will send using a camera or text story
 */
@Composable
fun MediaCaptureScreen(
  backStack: NavBackStack<NavKey>,
  state: MediaSendState,
  onEvent: (MediaCaptureScreenEvent) -> Unit,
  textStoryEditorSlot: @Composable () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    when (backStack.last()) {
      is MediaSendNavKey.Capture.TextStory -> textStoryEditorSlot()
      else -> {
        MediaCameraCaptureScreen(
          state = state,
          onEvent = onEvent
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
    ) {
      Buttons.Small(onClick = { onEvent(MediaCaptureScreenEvent.ShowCamera) }) {
        Text(text = stringResource(R.string.MediaCaptureScreen__camera))
      }

      Buttons.Small(onClick = { onEvent(MediaCaptureScreenEvent.ShowTextStory) }) {
        Text(text = stringResource(R.string.MediaCaptureScreen__text_story))
      }
    }
  }
}
