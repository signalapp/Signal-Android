/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import org.signal.core.models.media.Media
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRoute
import kotlin.time.Duration

/**
 * What the capture screen renders. Which capture screen is showing is navigation's and the selection is the flow's, so
 * both arrive from the parent; the camera reports its own state as it changes, and everything else is fixed for the life
 * of the flow and read once at construction.
 */
internal data class MediaCaptureState(
  val selectedCaptureScreen: MediaSendRoute.Capture = MediaSendRoute.Capture.Camera,
  val selectedMedia: List<Media> = emptyList(),
  val isCameraFirst: Boolean = false,
  val isStory: Boolean = false,
  val storiesEnabled: Boolean = false,
  val mode: MediaSendFlowActivityContract.Mode = MediaSendFlowActivityContract.Mode.SingleRecipient,
  /** Who this is headed to, when that is already settled. Only the chrome's tint reads it. */
  val recipientId: MediaRecipientId? = null,
  val storyMaxVideoDuration: Duration = Duration.ZERO,
  /** Only ever [MediaCaptureMode.PHOTO] or [MediaCaptureMode.VIDEO]; whether the text story is showing is navigation's. */
  val selectedCameraMode: MediaCaptureMode = MediaCaptureMode.PHOTO,
  val isVideoCaptureSupported: Boolean = true,
  /** As the camera reports it, so only ever true of the camera screen. */
  val isRecording: Boolean = false
) {

  /**
   * The modes this flow offers, in the order the bottom bar shows them, which leaves [MediaCaptureMode.PHOTO] in the
   * middle of a full flow.
   */
  val availableCaptureModes: List<MediaCaptureMode>
    get() = buildList {
      if (isVideoCaptureSupported) {
        add(MediaCaptureMode.VIDEO)
      }

      add(MediaCaptureMode.PHOTO)

      if (canOfferTextStory) {
        add(MediaCaptureMode.TEXT_STORY)
      }
    }

  /**
   * Whether the bar for switching between [availableCaptureModes] is up. A flow left with a single mode has nothing to
   * switch between, and a running recording has the screen to itself.
   */
  val canDisplayModeBar: Boolean
    get() = availableCaptureModes.size > 1 && !isRecording

  /** Whether the button for moving on to the editor is up. */
  val canDisplayNextButton: Boolean
    get() = selectedMedia.isNotEmpty() && !isRecording

  /** Which of [availableCaptureModes] is showing, taking navigation's word for it over the camera's own selection. */
  val selectedCaptureMode: MediaCaptureMode
    get() = if (selectedCaptureScreen == MediaSendRoute.Capture.TextStory) MediaCaptureMode.TEXT_STORY else selectedCameraMode

  /**
   * Only a camera-first flow headed somewhere a text story can go has one to offer, and only while the selection is
   * empty: a text story is text alone, so the first capture or pick leaves no way to send one.
   */
  private val canOfferTextStory: Boolean
    get() {
      val isSingleStory = mode == MediaSendFlowActivityContract.Mode.SingleRecipient && isStory
      val isHeadedSomewhereStoriesGo = mode == MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection || isSingleStory

      return isCameraFirst && storiesEnabled && isHeadedSomewhereStoriesGo && selectedMedia.isEmpty()
    }

  /** The cap a story puts on a recording's length, or zero to leave the device's own in place. */
  val maxVideoDurationSecondsOverride: Int
    get() = if (isStory) storyMaxVideoDuration.inWholeSeconds.toInt() else 0
}
