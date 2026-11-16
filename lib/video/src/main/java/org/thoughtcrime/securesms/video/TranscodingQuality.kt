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

    private const val MIN_OUTPUT_RESOLUTION = 240
    private const val MIN_VIDEO_BIT_RATE = 64 * VideoConstants.KB
    private const val MIN_AUDIO_BIT_RATE = 32 * VideoConstants.KB

    @JvmStatic
    fun bitRate(bytes: Long, durationMs: Long): Int {
      return (bytes * 8 / (durationMs / 1000f)).toInt()
    }
  }

  val targetTotalBitRate = targetVideoBitRate + targetAudioBitRate
  val byteCountEstimate = ((targetTotalBitRate / 8f) * (durationMs / 1000f)).toInt()

  /**
   * Returns a quality that never exceeds the resolution or bitrates of the source, so that transcoding a low quality video cannot make it larger.
   */
  fun cappedTo(source: SourceVideoLimits): TranscodingQuality {
    return TranscodingQuality(
      codec = codec,
      outputResolution = cap(outputResolution, source.shortEdge, MIN_OUTPUT_RESOLUTION),
      targetVideoBitRate = cap(targetVideoBitRate, source.videoBitRate, MIN_VIDEO_BIT_RATE),
      targetAudioBitRate = if (source.audioBitRate == 0) 0 else cap(targetAudioBitRate, source.audioBitRate, MIN_AUDIO_BIT_RATE),
      durationMs = durationMs
    )
  }

  private fun cap(target: Int, sourceValue: Int?, floor: Int): Int {
    return if (sourceValue == null) {
      target
    } else {
      target.coerceAtMost(sourceValue.coerceAtLeast(floor))
    }
  }

  override fun toString(): String {
    return "Quality{codec=$codec, targetVideoBitRate=$targetVideoBitRate, targetAudioBitRate=$targetAudioBitRate, duration=$durationMs, filesize=$byteCountEstimate}"
  }
}
