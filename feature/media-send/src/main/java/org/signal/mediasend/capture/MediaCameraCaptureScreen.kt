/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.MediaSendState

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
    maxVideoDurationSeconds = remember(state.isStory) {
      getMaxVideoDurationInSeconds(
        mediaConstraints = MediaSendDependencies.mediaSendRepository.getMediaConstraints(),
        maxVideoDuration = if (state.isStory) MediaSendDependencies.mediaSendRepository.storyMaxVideoDuration.inWholeSeconds.toInt() else -1
      )
    },
    onCheckPermissions = {},
    onRequestMicPermission = {},
    hasCameraPermission = { true }
  )
}
