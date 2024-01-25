/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.utils

import android.media.MediaFormat
object VideoConstants {
  const val AUDIO_BIT_RATE = 192_000
  const val VIDEO_FRAME_RATE = 30
  const val VIDEO_TARGET_BIT_RATE = 2_000_000
  const val VIDEO_MINIMUM_TARGET_BIT_RATE = 500_000
  const val LOW_RES_TARGET_VIDEO_BITRATE = 1_750_000
  const val LOW_RES_OUTPUT_FORMAT = 480
  const val VIDEO_SHORT_EDGE = 720
  const val VIDEO_LONG_EDGE = 1280
  const val VIDEO_MAX_RECORD_LENGTH_S = 60
  const val TOTAL_BYTES_PER_SECOND = VIDEO_TARGET_BIT_RATE / 8 + AUDIO_BIT_RATE / 8
  const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
  const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
  const val RECORDED_VIDEO_CONTENT_TYPE: String = "video/mp4"
}
