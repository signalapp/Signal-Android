/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

/**
 * A data class to hold various video transcoding parameters, such as bitrate.
 */
data class TranscodingQuality(val targetVideoBitRate: Int, val targetAudioBitRate: Int, val quality: Double, private val duration: Long, val outputResolution: Int) {
  init {
    if (quality < 0.0 || quality > 1.0) {
      throw IllegalArgumentException("Quality $quality is outside of accepted range [0.0, 1.0]!")
    }
  }

  val targetTotalBitRate = targetVideoBitRate + targetAudioBitRate
  val fileSizeEstimate = targetTotalBitRate * duration / 8000

  override fun toString(): String {
    return "Quality{" +
      "targetVideoBitRate=" + targetVideoBitRate +
      ", targetAudioBitRate=" + targetAudioBitRate +
      ", quality=" + quality +
      ", duration=" + duration +
      ", filesize=" + fileSizeEstimate +
      '}'
  }
}
