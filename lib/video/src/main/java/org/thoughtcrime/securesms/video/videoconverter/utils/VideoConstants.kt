/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.utils

import android.media.MediaFormat
object VideoConstants {
  const val AUDIO_BITRATE = 128_000
  const val VIDEO_BITRATE_L1 = 1_250_000
  const val VIDEO_BITRATE_L2 = 1_250_000
  const val VIDEO_BITRATE_L3 = 2_500_000
  const val VIDEO_SHORT_EDGE_SD = 480
  const val VIDEO_SHORT_EDGE_HD = 720
  const val VIDEO_LONG_EDGE_HD = 1280
  const val VIDEO_MAX_RECORD_LENGTH_S = 60
  const val MAX_ALLOWED_BYTES_PER_SECOND = VIDEO_BITRATE_L3 / 8 + AUDIO_BITRATE / 8
  const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
  const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
  const val RECORDED_VIDEO_CONTENT_TYPE: String = "video/mp4"
}
