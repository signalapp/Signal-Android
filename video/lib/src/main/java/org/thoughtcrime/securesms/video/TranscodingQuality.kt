/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video

import org.thoughtcrime.securesms.video.videoconverter.MediaConverter
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter.VideoCodec
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants

/**
 * A data class to hold various video transcoding parameters, such as bitrate.
 */
class TranscodingQuality private constructor(@VideoCodec val codec: String, val outputResolution: Int, val targetVideoBitRate: Int, val targetAudioBitRate: Int, private val durationMs: Long) {
  companion object {

    @JvmStatic
    fun createFromPreset(preset: TranscodingPreset, durationMs: Long): TranscodingQuality {
      return TranscodingQuality(preset.videoCodec, preset.videoShortEdge, preset.videoBitRate, preset.audioBitRate, durationMs)
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
  val byteCountEstimate = (targetTotalBitRate / 8) * (durationMs / 1000)

  override fun toString(): String {
    return "Quality{codec=$codec, targetVideoBitRate=$targetVideoBitRate, targetAudioBitRate=$targetAudioBitRate, duration=$durationMs, filesize=$byteCountEstimate}"
  }
}

enum class TranscodingPreset(@VideoCodec val videoCodec: String, val videoShortEdge: Int, val videoBitRate: Int, val audioBitRate: Int) {
  LEVEL_1(MediaConverter.VIDEO_CODEC_H264, VideoConstants.VIDEO_SHORT_EDGE_SD, VideoConstants.VIDEO_BITRATE_L1, VideoConstants.AUDIO_BITRATE),
  LEVEL_2(MediaConverter.VIDEO_CODEC_H264, VideoConstants.VIDEO_SHORT_EDGE_HD, VideoConstants.VIDEO_BITRATE_L2, VideoConstants.AUDIO_BITRATE),
  LEVEL_3(MediaConverter.VIDEO_CODEC_H264, VideoConstants.VIDEO_SHORT_EDGE_HD, VideoConstants.VIDEO_BITRATE_L3, VideoConstants.AUDIO_BITRATE),

  /** Experimetnal H265 level */
  LEVEL_3_H265(MediaConverter.VIDEO_CODEC_H265, VideoConstants.VIDEO_SHORT_EDGE_HD, VideoConstants.VIDEO_BITRATE_L3, VideoConstants.AUDIO_BITRATE);

  fun calculateMaxVideoUploadDurationInSeconds(upperFileSizeLimit: Long): Int {
    val upperFileSizeLimitWithMargin = (upperFileSizeLimit / 1.1).toLong()
    val totalBitRate = videoBitRate + audioBitRate
    return Math.toIntExact((upperFileSizeLimitWithMargin * 8) / totalBitRate)
  }
}
