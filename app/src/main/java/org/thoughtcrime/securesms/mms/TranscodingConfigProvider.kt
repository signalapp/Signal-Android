package org.thoughtcrime.securesms.mms

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.video.TranscodingConfig

/**
 * Gets corresponding configs depending on locale and sent media quality
 */
object TranscodingConfigProvider {
  @JvmStatic
  fun getAllConfigs(): TranscodingConfig.TranscodeConfig {
    val countryCode = PhoneNumberUtil.getInstance().parse(SignalStore.account.e164, "").countryCode
    return TranscodingConfig.getTranscodeConfig(RemoteConfig.transcodeConfig, countryCode)
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
