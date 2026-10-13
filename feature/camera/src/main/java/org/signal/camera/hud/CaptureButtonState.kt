/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

/** Which kind of capture the button offers while it is waiting to be used. */
enum class CaptureButtonMode {
  PHOTO,
  VIDEO
}

/**
 * Every look the capture button has, and all it needs to draw and animate itself: a caller says what is true of the
 * camera and [of] turns that into the one state to show. [tapRequest] is what a tap on each of them asks for.
 */
enum class CaptureButtonState {
  /** Waiting to take a photo. */
  PHOTO,

  /** Waiting to record, which is what the button will do instead of taking a photo. */
  VIDEO,

  /** Recording for only as long as the button is held. */
  RECORDING_HELD,

  /** Recording without being held, which runs until it is stopped. */
  RECORDING_LOCKED;

  /** Whether a recording is running, however it was started. */
  val isRecording: Boolean
    get() = this == RECORDING_HELD || this == RECORDING_LOCKED

  companion object {

    /** A running recording is what the button shows whatever mode the camera is in. */
    fun of(
      captureButtonMode: CaptureButtonMode,
      isRecording: Boolean,
      isRecordingLocked: Boolean
    ): CaptureButtonState = when {
      isRecording && isRecordingLocked -> RECORDING_LOCKED
      isRecording -> RECORDING_HELD
      captureButtonMode == CaptureButtonMode.VIDEO -> VIDEO
      else -> PHOTO
    }
  }
}

/**
 * What stands where the gallery button does. A recording has more use for that corner: while one is held it offers the
 * lock that would leave it running, and once it is running unheld it offers to pause it.
 */
enum class GallerySlotContent {
  GALLERY,
  LOCK,

  /** The lock with a thumb over it, which says that lifting there is what takes it. */
  LOCK_ENGAGED,
  PAUSE;

  companion object {

    /** @param isOverLock Whether a drag from the capture button has reached the lock, which only it has any use for. */
    fun of(captureButtonState: CaptureButtonState, isOverLock: Boolean = false): GallerySlotContent = when (captureButtonState) {
      CaptureButtonState.RECORDING_HELD -> if (isOverLock) LOCK_ENGAGED else LOCK
      CaptureButtonState.RECORDING_LOCKED -> PAUSE
      CaptureButtonState.PHOTO, CaptureButtonState.VIDEO -> GALLERY
    }
  }
}

/**
 * What a tap asks the camera for, decided by what the button is showing rather than by the gesture: a photo in photo
 * mode, and in video mode a recording that runs without being held, which a second tap then stops.
 *
 * Null while a held recording runs, since the finger holding it is the only thing that ends it.
 */
val CaptureButtonState.tapRequest: StandardCameraHudEvents?
  get() = when (this) {
    CaptureButtonState.PHOTO -> StandardCameraHudEvents.PhotoCaptureTriggered
    CaptureButtonState.VIDEO -> StandardCameraHudEvents.VideoCaptureStarted(isLocked = true)
    CaptureButtonState.RECORDING_LOCKED -> StandardCameraHudEvents.VideoCaptureStopped
    CaptureButtonState.RECORDING_HELD -> null
  }
