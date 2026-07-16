/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.utils

import android.media.MediaFormat
import org.thoughtcrime.securesms.video.TranscodingConfig.QualityTier
import org.thoughtcrime.securesms.video.TranscodingConfig.TranscodeConfig

object VideoConstants {
  const val KB: Int = 1000
  const val MB: Int = 1000 * KB
  const val VIDEO_SHORT_EDGE_HD = 720
  const val VIDEO_LONG_EDGE_HD = 1280
  const val VIDEO_MAX_RECORD_LENGTH_S = 60
  const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
  const val RECORDED_VIDEO_CONTENT_TYPE: String = "video/mp4"

  val DEFAULT_LVL1_STANDARD = QualityTier(resolution = 480, videoBitrateMbps = 1.0f, audioBitrateKbps = 128, maxDurationSec = 900)
  val DEFAULT_LVL2_SHORT_STANDARD = QualityTier(resolution = 720, videoBitrateMbps = 2.0f, audioBitrateKbps = 128, maxDurationSec = 600)
  val DEFAULT_LVL2_LONG_STANDARD = QualityTier(resolution = 720, videoBitrateMbps = 1.5f, audioBitrateKbps = 128, maxDurationSec = 900)

  @JvmStatic
  val DEFAULT_HIGH = QualityTier(resolution = 720, videoBitrateMbps = 4.0f, audioBitrateKbps = 128, maxDurationSec = 360)

  val DEFAULT_TRANSCODING_CONFIG = listOf(
    TranscodeConfig(
      locales = emptyList(),
      standard = listOf(DEFAULT_LVL1_STANDARD),
      high = listOf(DEFAULT_HIGH)
    ),
    TranscodeConfig(
      locales = listOf(1, 61, 81, 82, 65, 31, 47, 41, 32, 385, 971, 974, 49, 33),
      standard = listOf(DEFAULT_LVL2_SHORT_STANDARD, DEFAULT_LVL2_LONG_STANDARD),
      high = listOf(DEFAULT_HIGH)
    )
  )
}
