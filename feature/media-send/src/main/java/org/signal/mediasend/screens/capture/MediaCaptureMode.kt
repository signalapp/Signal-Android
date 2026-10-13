/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.annotation.StringRes
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.R
import org.signal.mediasend.test.TestTags

/**
 * A way of making media that the capture screen can offer. Which of these a given flow offers, and the order the bar
 * shows them in, is [MediaCaptureState.availableCaptureModes].
 *
 * @param captureScreen Where navigation has to be for the mode to be usable. [PHOTO] and [VIDEO] are two modes of the
 *   one camera, so they share a screen.
 */
internal enum class MediaCaptureMode(
  val captureScreen: MediaSendRoute.Capture,
  @param:StringRes val label: Int,
  val testTag: String
) {
  VIDEO(MediaSendRoute.Capture.Camera, R.string.MediaCaptureScreen__video, TestTags.MEDIA_CAPTURE_VIDEO_TOGGLE),
  PHOTO(MediaSendRoute.Capture.Camera, R.string.MediaCaptureScreen__photo, TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE),
  TEXT_STORY(MediaSendRoute.Capture.TextStory, R.string.MediaCaptureScreen__text, TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE)
}
