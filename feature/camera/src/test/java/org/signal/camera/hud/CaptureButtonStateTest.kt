/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Covers which look the capture button settles on for a given camera — in particular that a running recording is what it
 * shows whatever mode it was started from — and what a tap on each of those looks asks for.
 */
class CaptureButtonStateTest {

  @Test
  fun `Given photo mode, when nothing is being recorded, then the button is waiting to take a photo`() {
    assertThat(stateOf(CaptureButtonMode.PHOTO, isRecording = false, isRecordingLocked = false))
      .isEqualTo(CaptureButtonState.PHOTO)
  }

  @Test
  fun `Given video mode, when nothing is being recorded, then the button is waiting to record`() {
    assertThat(stateOf(CaptureButtonMode.VIDEO, isRecording = false, isRecordingLocked = false))
      .isEqualTo(CaptureButtonState.VIDEO)
  }

  /** A press and hold records from photo mode too, so it is the recording the button shows. */
  @Test
  fun `Given a recording that is being held, when in either mode, then the button shows the held recording`() {
    assertThat(stateOf(CaptureButtonMode.PHOTO, isRecording = true, isRecordingLocked = false))
      .isEqualTo(CaptureButtonState.RECORDING_HELD)
    assertThat(stateOf(CaptureButtonMode.VIDEO, isRecording = true, isRecordingLocked = false))
      .isEqualTo(CaptureButtonState.RECORDING_HELD)
  }

  @Test
  fun `Given a recording that is locked, when in either mode, then the button shows the locked recording`() {
    assertThat(stateOf(CaptureButtonMode.PHOTO, isRecording = true, isRecordingLocked = true))
      .isEqualTo(CaptureButtonState.RECORDING_LOCKED)
    assertThat(stateOf(CaptureButtonMode.VIDEO, isRecording = true, isRecordingLocked = true))
      .isEqualTo(CaptureButtonState.RECORDING_LOCKED)
  }

  /** A lock only means anything while a recording is running. */
  @Test
  fun `Given a lock left behind, when nothing is being recorded, then the button is waiting on its mode`() {
    assertThat(stateOf(CaptureButtonMode.VIDEO, isRecording = false, isRecordingLocked = true))
      .isEqualTo(CaptureButtonState.VIDEO)
  }

  @Test
  fun `Given a state that is waiting to be used, when asked whether it is recording, then it is not`() {
    assertThat(CaptureButtonState.PHOTO.isRecording).isFalse()
    assertThat(CaptureButtonState.VIDEO.isRecording).isFalse()
  }

  @Test
  fun `Given a recording, when asked whether it is recording, then it is, held or locked`() {
    assertThat(CaptureButtonState.RECORDING_HELD.isRecording).isTrue()
    assertThat(CaptureButtonState.RECORDING_LOCKED.isRecording).isTrue()
  }

  @Test
  fun `Given photo mode, when the button is tapped, then a photo is asked for`() {
    assertThat(CaptureButtonState.PHOTO.tapRequest)
      .isEqualTo(StandardCameraHudEvents.PhotoCaptureTriggered)
  }

  /** In video mode a tap records rather than takes a photo, and what it starts needs no holding. */
  @Test
  fun `Given video mode, when the button is tapped, then a recording that needs no holding is asked for`() {
    assertThat(CaptureButtonState.VIDEO.tapRequest)
      .isEqualTo(StandardCameraHudEvents.VideoCaptureStarted(isLocked = true))
  }

  @Test
  fun `Given a recording that needs no holding, when the button is tapped, then it is asked to stop`() {
    assertThat(CaptureButtonState.RECORDING_LOCKED.tapRequest)
      .isEqualTo(StandardCameraHudEvents.VideoCaptureStopped)
  }

  /** The finger holding the recording open is what ends it, so a tap has nothing to ask for. */
  @Test
  fun `Given a recording that is being held, when the button is tapped, then nothing is asked for`() {
    assertThat(CaptureButtonState.RECORDING_HELD.tapRequest).isNull()
  }

  @Test
  fun `Given nothing is being recorded, when the gallery's corner is filled, then the gallery is what fills it`() {
    assertThat(GallerySlotContent.of(CaptureButtonState.PHOTO)).isEqualTo(GallerySlotContent.GALLERY)
    assertThat(GallerySlotContent.of(CaptureButtonState.VIDEO)).isEqualTo(GallerySlotContent.GALLERY)
  }

  /** The lock has to be within reach of the finger holding the recording open, so it takes the nearest corner. */
  @Test
  fun `Given a recording that is being held, when the gallery's corner is filled, then the lock is what fills it`() {
    assertThat(GallerySlotContent.of(CaptureButtonState.RECORDING_HELD)).isEqualTo(GallerySlotContent.LOCK)
  }

  /** The thumb covers the lock on the way to it, so the lock answering for itself is all there is to go by. */
  @Test
  fun `Given a thumb over the lock, when the gallery's corner is filled, then the engaged lock is what fills it`() {
    assertThat(GallerySlotContent.of(CaptureButtonState.RECORDING_HELD, isOverLock = true)).isEqualTo(GallerySlotContent.LOCK_ENGAGED)
  }

  @Test
  fun `Given a recording that is locked, when the gallery's corner is filled, then the pause is what fills it`() {
    assertThat(GallerySlotContent.of(CaptureButtonState.RECORDING_LOCKED)).isEqualTo(GallerySlotContent.PAUSE)
  }

  /** Only a held recording has a lock on offer, so nothing else has any use for a thumb being over one. */
  @Test
  fun `Given no lock on offer, when a thumb is reported over one, then the corner is filled as it would have been`() {
    assertThat(GallerySlotContent.of(CaptureButtonState.PHOTO, isOverLock = true)).isEqualTo(GallerySlotContent.GALLERY)
    assertThat(GallerySlotContent.of(CaptureButtonState.RECORDING_LOCKED, isOverLock = true)).isEqualTo(GallerySlotContent.PAUSE)
  }

  private fun stateOf(
    captureButtonMode: CaptureButtonMode,
    isRecording: Boolean,
    isRecordingLocked: Boolean
  ): CaptureButtonState = CaptureButtonState.of(
    captureButtonMode = captureButtonMode,
    isRecording = isRecording,
    isRecordingLocked = isRecordingLocked
  )
}
