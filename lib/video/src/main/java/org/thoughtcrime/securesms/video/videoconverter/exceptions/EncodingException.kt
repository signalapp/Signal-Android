/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video.videoconverter.exceptions

class EncodingException : Exception {
  /** Whether the input video was HDR content. */
  @JvmField var isHdrInput: Boolean = false

  /** Whether HDR-to-SDR tone-mapping was successfully applied to the decoder. */
  @JvmField var toneMapApplied: Boolean = false

  /** The name of the video decoder codec that was selected, or null if decoder creation failed. */
  @JvmField var decoderName: String? = null

  /** The name of the video encoder codec that was selected, or null if encoder creation failed. */
  @JvmField var encoderName: String? = null

  constructor(message: String?) : super(message)
  constructor(message: String?, inner: Exception?) : super(message, inner)
}
