package org.thoughtcrime.securesms.mms

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.video.TranscodingConfig
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import kotlin.time.Duration

/**
 * Gets corresponding configs depending on locale and sent media quality
 */
object TranscodingConfigProvider {
  @JvmStatic
  fun getAllConfigs(): TranscodingConfig.TranscodeConfig {
    val countryCode = PhoneNumberUtil.getInstance().parse(SignalStore.account.e164, "").countryCode
    return TranscodingConfig.getTranscodeConfig(RemoteConfig.transcodeConfig, countryCode)
  }

  /**
   * The longest video duration allowed by any quality tier in the current config.
   */
  @JvmStatic
  fun getMaxVideoDurationSeconds(): Int {
    val config = getAllConfigs()
    return (config.standard + config.high).maxOfOrNull { it.maxDurationSec } ?: VideoConstants.DEFAULT_HIGH.maxDurationSec
  }

  /**
   * The highest video bitrate, in bits per second, targeted by any quality tier in the current config.
   */
  @JvmStatic
  fun getMaxVideoBitrateBps(): Int {
    val config = getAllConfigs()
    val maxMbps = (config.standard + config.high).maxOfOrNull { it.videoBitrateMbps } ?: VideoConstants.DEFAULT_HIGH.videoBitrateMbps
    return (maxMbps * VideoConstants.MB).toInt()
  }

  /**
   * The longest duration, in seconds, that a video of [duration] may be sent at when using [quality]. Anything
   * longer is truncated to fit.
   */
  @JvmStatic
  fun getMaxVideoDurationSeconds(quality: SentMediaQuality, duration: Duration): Int {
    return TranscodingConfig.calculateMaxVideoUploadDurationInSeconds(getConfigsForMediaQuality(quality), duration)
  }

  @JvmStatic
  fun getConfigsForMediaQuality(quality: SentMediaQuality): List<TranscodingConfig.QualityTier> {
    val config = getAllConfigs()
    return when (quality) {
      SentMediaQuality.STANDARD -> config.standard
      SentMediaQuality.HIGH -> config.high
    }
  }
}
