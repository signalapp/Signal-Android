/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import org.signal.core.util.SeekableFileDescriptor

sealed interface CameraXScreenEvents {
  class ImageCaptured(val data: ByteArray, val width: Int, val height: Int) : CameraXScreenEvents

  /**
   * @param fd Owned by the consumer, which must close it once it is finished reading the recording.
   * @param durationMs How long the recording ran, as reported by the recorder.
   */
  class VideoCaptured(val fd: SeekableFileDescriptor, val durationMs: Long) : CameraXScreenEvents
  class QrCodeFound(val data: String) : CameraXScreenEvents
  data object VideoCaptureError : CameraXScreenEvents
  data object GalleryClicked : CameraXScreenEvents
  data object CameraCloseClicked : CameraXScreenEvents
}
