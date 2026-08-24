package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.signal.core.util.isNotNullOrBlank
import org.signal.ringrtc.AudioConfig
import org.signal.ringrtc.SvcConfig
import org.signal.ringrtc.VideoConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceConfig
import java.io.File

/**
 * Utility class to determine the audio configuration that RingRTC should use.
 */
object RingRtcDynamicConfiguration {
  private var lastFetchTime: Long = 0

  fun isTelecomAllowedForDevice(): Boolean {
    return RemoteConfig.useJetPackTelecom
  }

  @JvmStatic
  fun getAudioConfig(): AudioConfig {
    if (RemoteConfig.internalUser && SignalStore.internal.callingSetAudioConfig) {
      // Use the internal audio settings.
      var audioConfig = AudioConfig()
      audioConfig.useOboe = SignalStore.internal.callingUseOboeAdm
      audioConfig.useSoftwareAec = SignalStore.internal.callingUseSoftwareAec
      audioConfig.useSoftwareNs = SignalStore.internal.callingUseSoftwareNs
      audioConfig.useInputLowLatency = SignalStore.internal.callingUseInputLowLatency
      audioConfig.useInputVoiceComm = SignalStore.internal.callingUseInputVoiceComm

      return audioConfig
    }

    // Use the audio settings provided by the remote configuration.
    if (lastFetchTime != SignalStore.remoteConfig.lastFetchTime) {
      // The remote config has been updated.
      AudioDeviceConfig.refresh()
      lastFetchTime = SignalStore.remoteConfig.lastFetchTime
    }
    return AudioDeviceConfig.getCurrentConfig()
  }

  @JvmStatic
  fun getVideoConfig(): VideoConfig {
    if (RemoteConfig.internalUser && SignalStore.internal.callingSetVideoConfig) {
      val videoConfig = VideoConfig()
      videoConfig.enableHardwareVp9Encode = SignalStore.internal.callingUseHardwareVp9Encode
      videoConfig.enableHardwareVp9Decode = SignalStore.internal.callingUseHardwareVp9Decode
      videoConfig.enableSoftwareVp9Encode = SignalStore.internal.callingUseSoftwareVp9Encode
      videoConfig.enableSoftwareVp9Decode = SignalStore.internal.callingUseSoftwareVp9Decode
      return videoConfig
    }

    val (manufacturer, model) = getSoCInfo()
    val soc = "${manufacturer.lowercase()}-${model.lowercase()}"
    val videoConfig = VideoConfig()
    videoConfig.enableHardwareVp9Encode = !RemoteConfig.disableHardwareVp9EncodeSocList.contains(soc) && !RemoteConfig.disableHardwareVp9EncodeSocList.contains(model)
    videoConfig.enableHardwareVp9Decode = !RemoteConfig.disableHardwareVp9DecodeSocList.contains(soc) && !RemoteConfig.disableHardwareVp9DecodeSocList.contains(model)
    videoConfig.enableSoftwareVp9Encode = RemoteConfig.enableSoftwareVp9EncodeSoCList.contains(soc) || RemoteConfig.enableSoftwareVp9EncodeSoCList.contains(model)
    videoConfig.enableSoftwareVp9Decode = RemoteConfig.enableSoftwareVp9Decode || RemoteConfig.enableSoftwareVp9DecodeSoCList.contains(soc) || RemoteConfig.enableSoftwareVp9DecodeSoCList.contains(model)

    return videoConfig
  }

  @JvmStatic
  fun getSvcConfig(): SvcConfig? {
    val enableSvc = (RemoteConfig.internalUser && SignalStore.internal.callingEnableSvc) || (!RemoteConfig.internalUser && RemoteConfig.enableSvc)
    val videoConfig = getVideoConfig()
    if (enableSvc && videoConfig.enableSoftwareVp9Encode) {
      val maxBitrateBps = RemoteConfig.svcMaxBitrateBps
      return SvcConfig(RemoteConfig.svcMode, RemoteConfig.svcModeForScreenshare, if (maxBitrateBps != 0) { maxBitrateBps } else { null })
    }
    return null
  }

  @JvmStatic
  fun getStatsIntervalSecs(): Int? {
    val secs = SignalStore.internal.callingStatsIntervalSecs
    return if (secs != 0) secs else null
  }

  private fun getSoCInfo(): Pair<String, String> {
    // 1. Native API Method (Android 12 / API 31+)
    if (Build.VERSION.SDK_INT >= 31) {
      return Build.SOC_MANUFACTURER to Build.SOC_MODEL
    } else {
      // 2. Fallback Method for Older Devices (Android 11 and below)
      val socModel = getSystemProperty("ro.board.platform").takeIf { it.isNotNullOrBlank() && !it.equals("unknown", ignoreCase = true) }
        ?: parseCpuInfoForHardware().takeIf { it.isNotNullOrBlank() }
        ?: "unknown"
      return "unknown" to socModel
    }
  }

  private fun getSystemProperty(key: String): String {
    return try {
      val systemProperties = Class.forName("android.os.SystemProperties")
      val get = systemProperties.getMethod("get", String::class.java)
      get.invoke(null, key) as String
    } catch (_: Exception) {
      ""
    }
  }

  /**
   * Helper to parse /proc/cpuinfo for the "Hardware" field
   */
  private fun parseCpuInfoForHardware(): String {
    return try {
      File("/proc/cpuinfo").useLines { lines ->
        lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
          ?.substringAfter(':', "")
          ?.trim()
          ?: ""
      }
    } catch (_: Exception) {
      ""
    }
  }
}
