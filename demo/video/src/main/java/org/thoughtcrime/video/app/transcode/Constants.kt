/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import org.thoughtcrime.securesms.video.TranscodingConfig.QualityTier
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants

/**
 * A dumping ground for constants that should be referenced across the sample app.
 */

internal const val MIN_VIDEO_MEGABITRATE = 0.5f
internal const val MAX_VIDEO_MEGABITRATE = 4f
internal val OPTIONS_AUDIO_KILOBITRATES = listOf(64, 96, 128, 160, 192)

enum class VideoResolution(val longEdge: Int, val shortEdge: Int) {
  SD(854, 480),
  HD(1280, 720),
  FHD(1920, 1080),
  WQHD(2560, 1440),
  UHD(3840, 2160);

  fun getContentDescription(): String {
    return "Resolution with a long edge of $longEdge and a short edge of $shortEdge."
  }
}

enum class TranscodeQuality(val qualityTier: QualityTier) {
  STANDARD(VideoConstants.DEFAULT_LVL1_STANDARD),
  HIGH(VideoConstants.DEFAULT_HIGH)
}

internal fun calculateVideoMegaBitrateFromQuality(quality: TranscodeQuality): Float = quality.qualityTier.videoBitrateMbps

internal fun calculateAudioKiloBitrateFromQuality(quality: TranscodeQuality): Int = quality.qualityTier.audioBitrateKbps

internal fun convertQualityToVideoResolution(quality: TranscodeQuality): VideoResolution {
  return VideoResolution.entries.firstOrNull { it.shortEdge == quality.qualityTier.resolution } ?: VideoResolution.SD
}
