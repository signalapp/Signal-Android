/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import org.signal.mediasend.screens.capture.MediaCaptureScreenEvents
import org.signal.mediasend.screens.edit.MediaEditScreenEvents

/**
 * The screen events that the flow, rather than the screen that raised them, is responsible for.
 * Implemented by [MediaSendFlowViewModel].
 */
interface MediaSendEventHandler {
  fun onMediaEditScreenEvent(mediaEditScreenEvent: MediaEditScreenEvents)
  fun onMediaCaptureScreenEvent(mediaCaptureScreenEvent: MediaCaptureScreenEvents)
}
