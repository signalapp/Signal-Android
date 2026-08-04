/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

sealed interface MediaCaptureScreenEvents {
  data object ShowCamera : MediaCaptureScreenEvents
  data object ShowTextStory : MediaCaptureScreenEvents
  data object NextClicked : MediaCaptureScreenEvents
  data object CycleTextStoryBackgroundColor : MediaCaptureScreenEvents
  data object AddLinkToTextStory : MediaCaptureScreenEvents
  class Camera(val event: CameraXScreenEvents) : MediaCaptureScreenEvents
}
