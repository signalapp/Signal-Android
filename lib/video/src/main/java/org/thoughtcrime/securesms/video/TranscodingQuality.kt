/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

import org.thoughtcrime.securesms.video.TranscodingConfig.QualityTier
import org.thoughtcrime.securesms.video.TranscodingConfig.getQualityTier
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter.VideoCodec
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import kotlin.time.Duration.Companion.milliseconds

/**
 * A data class to hold various video transcoding parameters, such as bitrate.
 */
class TranscodingQuality private constructor(@VideoCodec val codec: String, val outputResolution: Int, val targetVideoBitRate: Int, val targetAudioBitRate: Int, private val durationMs: Long) {
  companion object {

    @JvmStatic
    fun createFromQualityTiers(transcodingConfig: List<QualityTier>, durationMs: Long): TranscodingQuality {
      val config = getQualityTier(transcodingConfig, durationMs.milliseconds)
      return TranscodingQuality(
        codec = MediaConverter.VIDEO_CODEC_H264,
        outputResolution = config.resolution,
        targetVideoBitRate = (config.videoBitrateMbps * VideoConstants.MB).toInt(),
        targetAudioBitRate = config.audioBitrateKbps * VideoConstants.KB,
        durationMs = durationMs
      )
    }

    @JvmStatic
    fun createManuallyForTesting(codec: String, outputShortEdge: Int, videoBitrate: Int, audioBitrate: Int, durationMs: Long): TranscodingQuality {
      return TranscodingQuality(codec, outputShortEdge, videoBitrate, audioBitrate, durationMs)
    }

    @JvmStatic
    fun bitRate(bytes: Long, durationMs: Long): Int {
      return (bytes * 8 / (durationMs / 1000f)).toInt()
    }
  }

  val targetTotalBitRate = targetVideoBitRate + targetAudioBitRate
  val byteCountEstimate = ((targetTotalBitRate / 8f) * (durationMs / 1000f)).toInt()

  override fun toString(): String {
    return "Quality{codec=$codec, targetVideoBitRate=$targetVideoBitRate, targetAudioBitRate=$targetAudioBitRate, duration=$durationMs, filesize=$byteCountEstimate}"
  }
}
