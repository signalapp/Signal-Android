/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRoute

sealed interface MediaCaptureScreenEvents {

  /** The parent flow's state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: MediaSendFlowState) : MediaCaptureScreenEvents {
    // The parent's state carries the message the user is typing and every item they have picked. Only the size of the
    // selection is worth logging, and it is the only part safe to.
    override fun toString(): String = "ParentStateChanged(selectedMedia=${parentState.selectedMedia.size})"
  }

  /** Navigation moved between the camera and the text story editor. */
  data class SelectedCaptureScreenChanged(val selectedCaptureScreen: MediaSendRoute.Capture) : MediaCaptureScreenEvents

  data object ShowCamera : MediaCaptureScreenEvents
  data object ShowTextStory : MediaCaptureScreenEvents
  data object NextClicked : MediaCaptureScreenEvents

  /** Something the camera reported. What becomes of it is the flow's to decide rather than this screen's. */
  class Camera(val event: CameraXScreenEvents) : MediaCaptureScreenEvents
}
