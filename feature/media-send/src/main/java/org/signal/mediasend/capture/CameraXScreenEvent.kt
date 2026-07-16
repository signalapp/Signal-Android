/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import java.io.FileDescriptor

sealed interface CameraXScreenEvent {
  class ImageCaptured(val data: ByteArray, val width: Int, val height: Int) : CameraXScreenEvent
  class VideoCaptured(val fd: FileDescriptor) : CameraXScreenEvent
  class QrCodeFound(val data: String) : CameraXScreenEvent
  data object VideoCaptureError : CameraXScreenEvent
  data object GalleryClicked : CameraXScreenEvent
  data object CameraCountButtonClicked : CameraXScreenEvent
}
