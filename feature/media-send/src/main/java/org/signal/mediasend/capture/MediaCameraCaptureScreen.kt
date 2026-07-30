/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.rememberPreviewState

/**
 * Allows the user to capture images and video from the hardware camera to utilize in the media send flow.
 */
@Composable
fun MediaCameraCaptureScreen(
  state: MediaSendState,
  onEvent: (MediaCaptureScreenEvent) -> Unit
) {
  CameraXScreen(
    state = remember(state.selectedMedia) {
      CameraXScreenState(
        isVideoEnabled = true,
        isQrScanEnabled = true,
        selectedMediaCount = state.selectedMedia.size
      )
    },
    onEvent = { event -> onEvent(MediaCaptureScreenEvent.Camera(event)) },
    videoRecordingConfig = rememberVideoRecordingConfig(
      mediaConstraints = state.mediaConstraints,
      maxDurationSecondsOverride = if (state.isStory) state.storyMaxVideoDuration.inWholeSeconds.toInt() else 0
    ),
    onCheckPermissions = {}, // TODO [media-send]
    onRequestMicPermission = {}, // TODO [media-send]
    hasCameraPermission = { true }, // TODO [media-send]
    storiesEnabled = state.storiesEnabled
  )
}

@Preview
@Composable
private fun MediaCameraCaptureScreenPreview() {
  Previews.Preview {
    MediaCameraCaptureScreen(
      state = rememberPreviewState(),
      onEvent = {}
    )
  }
}
