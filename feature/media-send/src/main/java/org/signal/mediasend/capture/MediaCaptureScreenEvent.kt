/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

sealed interface MediaCaptureScreenEvent {
  data object ShowCamera : MediaCaptureScreenEvent
  data object ShowTextStory : MediaCaptureScreenEvent
  data object NextClicked : MediaCaptureScreenEvent
  data object CycleTextStoryBackgroundColor : MediaCaptureScreenEvent
  data object AddLinkToTextStory : MediaCaptureScreenEvent
  class Camera(val event: CameraXScreenEvent) : MediaCaptureScreenEvent
}
