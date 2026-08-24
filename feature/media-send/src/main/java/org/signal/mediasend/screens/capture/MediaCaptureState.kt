/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import org.signal.core.models.media.Media
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRoute
import kotlin.time.Duration

/**
 * What the capture screen renders. Which capture screen is showing is navigation and the selection is the flow's, so
 * both arrive from the parent; everything else is fixed for the life of the flow and read once at construction.
 */
internal data class MediaCaptureState(
  val selectedCaptureScreen: MediaSendRoute.Capture = MediaSendRoute.Capture.Camera,
  val selectedMedia: List<Media> = emptyList(),
  val isCameraFirst: Boolean = false,
  val isStory: Boolean = false,
  val storiesEnabled: Boolean = false,
  val mode: MediaSendFlowActivityContract.Mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
  /** Null leaves recording on the most conservative limits this device supports. */
  val mediaConstraints: MediaConstraints? = null,
  val storyMaxVideoDuration: Duration = Duration.ZERO
) {

  /**
   * Whether the camera's own chrome is joined by the flow's. Only a camera-first flow headed somewhere a text story can
   * go has anything to add.
   */
  val canDisplayBottomBar: Boolean
    get() {
      val isSingleStory = mode == MediaSendFlowActivityContract.Mode.SingleRecipient && isStory
      return isCameraFirst && storiesEnabled && (mode == MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection || isSingleStory)
    }

  /** The toggle holds the spot the media bar takes over once something has been captured. */
  val canDisplayToggleSwitch: Boolean
    get() = selectedMedia.isEmpty()

  val canDisplayMediaBar: Boolean
    get() = selectedMedia.isNotEmpty()

  /** The cap a story puts on a recording's length, or zero to leave the device's own cap in place. */
  val maxVideoDurationSecondsOverride: Int
    get() = if (isStory) storyMaxVideoDuration.inWholeSeconds.toInt() else 0
}
