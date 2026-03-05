/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.compose.Buttons

/**
 * Screen that allows user to capture the media they will send using a camera or text story
 */
@Composable
fun MediaCaptureScreen(
  backStack: NavBackStack<NavKey>,
  cameraSlot: @Composable () -> Unit,
  textStoryEditorSlot: @Composable () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    val top = backStack.last()

    when (top) {
      is MediaSendNavKey.Capture.Camera -> cameraSlot()
      is MediaSendNavKey.Capture.TextStory -> textStoryEditorSlot()
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
    ) {
      Buttons.Small(onClick = {
        if (top == MediaSendNavKey.Capture.TextStory) {
          backStack.remove(top)
        }
      }) {
        Text(text = "Camera")
      }

      Buttons.Small(onClick = {
        if (top == MediaSendNavKey.Capture.Camera) {
          backStack.add(MediaSendNavKey.Capture.TextStory)
        }
      }) {
        Text(text = "Text Story")
      }
    }
  }
}
