package org.thoughtcrime.securesms.video

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.JsonUtils
import org.thoughtcrime.securesms.video.TranscodingConfig.QualityTier
import org.thoughtcrime.securesms.video.TranscodingConfig.TranscodeConfig
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class TranscodingConfigTest {

  private val countryConfig = TranscodeConfig(locales = listOf(1, 2), standard = listOf(QualityTier(480, 1.0f, 128, 900)))
  private val defaultConfig = TranscodeConfig(locales = emptyList(), standard = listOf(QualityTier(360, 0.5f, 96, 900)))
  private val serializedConfig = JsonUtils.toJson(listOf(countryConfig, defaultConfig))

  @Test
  fun givenMatchingCountryCode_whenIGetTranscodeConfig_thenIExpectMatchedLocale() {
    val result = TranscodingConfig.getTranscodeConfig(serializedConfig, 2)
    assertEquals(countryConfig, result)
  }

  @Test
  fun givenMissingCountryCode_whenIGetTranscodeConfig_thenIExpectDefaultLocale() {
    val result = TranscodingConfig.getTranscodeConfig(serializedConfig, 999)
    assertEquals(defaultConfig, result)
  }

  @Test
  fun givenEmptyConfig_whenIGetTranscodeConfig_thenIExpectDefault() {
    val result = TranscodingConfig.getTranscodeConfig("", -1)
    assertEquals(VideoConstants.DEFAULT_TRANSCODING_CONFIG.first(), result)
  }

  @Test
  fun givenInvalidConfig_whenIGetTranscodeConfig_thenIExpectDefault() {
    val result = TranscodingConfig.getTranscodeConfig("invalid", -1)
    assertEquals(VideoConstants.DEFAULT_TRANSCODING_CONFIG.first(), result)
  }

  @Test
  fun givenVideo_whenIGetQualityTier_thenItMatchesAgainstValidDuration() {
    val shortTier = QualityTier(resolution = 720, videoBitrateMbps = 2.0f, audioBitrateKbps = 128, maxDurationSec = 10)
    val longTier = QualityTier(resolution = 480, videoBitrateMbps = 1.0f, audioBitrateKbps = 128, maxDurationSec = 100)

    val result = TranscodingConfig.getQualityTier(listOf(shortTier, longTier), 50.seconds)

    assertEquals(longTier, result)
  }

  @Test
  fun givenVideo_whenIGetQualityTier_thenItMatchesAgainstShortestValidDuration() {
    val shortTier = QualityTier(resolution = 720, videoBitrateMbps = 2.0f, audioBitrateKbps = 128, maxDurationSec = 10)
    val longTier = QualityTier(resolution = 480, videoBitrateMbps = 1.0f, audioBitrateKbps = 128, maxDurationSec = 100)

    val result = TranscodingConfig.getQualityTier(listOf(shortTier, longTier), 5.seconds)

    assertEquals(shortTier, result)
  }

  @Test
  fun givenMissingTiers_whenIGetQualityTier_thenIExpectDefault() {
    val shortTier = QualityTier(resolution = 720, videoBitrateMbps = 2.0f, audioBitrateKbps = 128, maxDurationSec = 10)
    val longTier = QualityTier(resolution = 480, videoBitrateMbps = 1.0f, audioBitrateKbps = 128, maxDurationSec = 20)

    val result = TranscodingConfig.getQualityTier(listOf(shortTier, longTier), 30.seconds)

    assertEquals(longTier, result)
  }

  @Test
  fun givenLargeFileSize_whenIGetMaxVideoUploadDuration_thenIExpectCoercionToTierMaxDuration() {
    val tier = QualityTier(resolution = 480, videoBitrateMbps = 1.0f, audioBitrateKbps = 128, maxDurationSec = 30)

    val result = TranscodingConfig.calculateMaxVideoUploadDurationInSeconds(listOf(tier), 10.seconds, maxFileSize = 10000000L)

    assertEquals(tier.maxDurationSec, result)
  }
}
