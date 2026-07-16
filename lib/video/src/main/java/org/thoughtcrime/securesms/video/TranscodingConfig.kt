package org.thoughtcrime.securesms.video

import arrow.core.getOrElse
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.signal.core.util.logging.Log
import org.signal.core.util.serialization.SignalJson
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import kotlin.time.Duration

/**
 * Gets corresponding quality tiers and transcoding config depending on locale and duration of video.
 */
object TranscodingConfig {
  private val TAG = Log.tag(TranscodingConfig::class.java)

  @Serializable
  data class TranscodeConfig(
    val locales: List<Int> = emptyList(),
    val standard: List<QualityTier> = listOf(VideoConstants.DEFAULT_LVL1_STANDARD),
    val high: List<QualityTier> = listOf(VideoConstants.DEFAULT_HIGH)
  )

  @Serializable
  data class QualityTier(
    val resolution: Int,
    val videoBitrateMbps: Float,
    val audioBitrateKbps: Int,
    val maxDurationSec: Int
  )

  @JvmStatic
  fun getTranscodeConfig(serializedConfig: String, countryCode: Int): TranscodeConfig {
    val transcodeConfigs: List<TranscodeConfig> = if (serializedConfig.isNotEmpty()) {
      SignalJson
        .decode(ListSerializer(TranscodeConfig.serializer()), serializedConfig)
        .getOrElse {
          Log.e(TAG, "Failed to parse json!", it.cause)
          VideoConstants.DEFAULT_TRANSCODING_CONFIG
        }
    } else {
      VideoConstants.DEFAULT_TRANSCODING_CONFIG
    }

    return transcodeConfigs
      .sortedByDescending { it.locales.contains(countryCode) }
      .firstOrNull { it.locales.contains(countryCode) || it.locales.isEmpty() }
      ?: TranscodeConfig()
  }

  fun calculateMaxVideoUploadDurationInSeconds(tiers: List<QualityTier>, videoDuration: Duration, maxFileSize: Long): Int {
    val config = getQualityTier(tiers, videoDuration)
    val upperFileSizeLimitWithMargin = (maxFileSize / 1.1).toLong()
    val totalBitRate = ((config.videoBitrateMbps * VideoConstants.MB) + (config.audioBitrateKbps * VideoConstants.KB)).toLong()
    val maxDurationFromFileSize = Math.toIntExact((upperFileSizeLimitWithMargin * 8) / totalBitRate)
    return maxDurationFromFileSize.coerceAtMost(config.maxDurationSec)
  }

  @JvmStatic
  fun getQualityTier(tiers: List<QualityTier>, videoDuration: Duration): QualityTier {
    val configs = tiers.sortedBy { it.maxDurationSec }
    return configs.firstOrNull { videoDuration.inWholeSeconds <= it.maxDurationSec } ?: configs.lastOrNull() ?: VideoConstants.DEFAULT_LVL1_STANDARD
  }
}
