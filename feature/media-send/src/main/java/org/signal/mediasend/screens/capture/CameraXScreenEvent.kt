/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import org.signal.core.util.SeekableFileDescriptor

sealed interface CameraXScreenEvent {
  class ImageCaptured(val data: ByteArray, val width: Int, val height: Int) : CameraXScreenEvent

  /**
   * @param fd Owned by the consumer, which must close it once it is finished reading the recording.
   * @param durationMs How long the recording ran, as reported by the recorder.
   */
  class VideoCaptured(val fd: SeekableFileDescriptor, val durationMs: Long) : CameraXScreenEvent
  class QrCodeFound(val data: String) : CameraXScreenEvent
  data object VideoCaptureError : CameraXScreenEvent
  data object GalleryClicked : CameraXScreenEvent
  data object CameraCloseClicked : CameraXScreenEvent
}
