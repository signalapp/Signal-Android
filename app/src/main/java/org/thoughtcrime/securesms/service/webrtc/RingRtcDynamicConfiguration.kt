package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.signal.core.util.asListContains
import org.signal.ringrtc.AudioConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceConfig

/**
 * Utility class to determine the audio configuration that RingRTC should use.
 */
object RingRtcDynamicConfiguration {
  private var lastFetchTime: Long = 0

  fun isTelecomAllowedForDevice(): Boolean {
    return RemoteConfig.telecomManufacturerAllowList.lowercase().asListContains(Build.MANUFACTURER.lowercase()) &&
      !RemoteConfig.telecomModelBlocklist.lowercase().asListContains(Build.MODEL.lowercase())
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
}
