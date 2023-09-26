/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.webp

import android.graphics.Bitmap

class WebpDecoder {

  init {
    System.loadLibrary("signalwebp")
  }

  external fun nativeDecodeBitmapScaled(data: ByteArray, requestedWidth: Int, requestedHeight: Int): Bitmap?
}
