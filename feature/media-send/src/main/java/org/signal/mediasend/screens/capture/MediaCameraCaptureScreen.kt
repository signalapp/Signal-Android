/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.rememberPreviewState

/**
 * Allows the user to capture images and video from the hardware camera to utilize in the media send flow.
 */
@Composable
fun MediaCameraCaptureScreen(
  state: MediaSendFlowState,
  onEvent: (MediaCaptureScreenEvents) -> Unit
) {
  // Shared with the permission controller so that the microphone is asked for exactly when recording is on offer.
  val isVideoEnabled = true
  val permissions = rememberCameraPermissionController(isVideoEnabled)

  CameraXScreen(
    state = remember(state.selectedMedia) {
      CameraXScreenState(
        isVideoEnabled = isVideoEnabled,
        isQrScanEnabled = true,
        selectedMediaCount = state.selectedMedia.size
      )
    },
    onEvent = { event -> onEvent(MediaCaptureScreenEvents.Camera(event)) },
    videoRecordingConfig = rememberVideoRecordingConfig(
      mediaConstraints = state.mediaConstraints,
      maxDurationSecondsOverride = if (state.isStory) state.storyMaxVideoDuration.inWholeSeconds.toInt() else 0
    ),
    onCheckPermissions = permissions.requestCapturePermissions,
    onRequestMicPermission = permissions.requestMicrophonePermission,
    hasCameraPermission = permissions.hasCameraPermission,
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
