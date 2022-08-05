package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.signal.core.util.asListContains
import org.signal.ringrtc.CallManager.AudioProcessingMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags

/**
 * Utility class to determine which AEC method RingRTC should use.
 */
object RingRtcDynamicConfiguration {

  @JvmStatic
  fun getAudioProcessingMethod(): AudioProcessingMethod {
    if (SignalStore.internalValues().callingAudioProcessingMethod() != AudioProcessingMethod.Default) {
      return SignalStore.internalValues().callingAudioProcessingMethod()
    }

    val useAec3: Boolean = FeatureFlags.useAec3()

    return when {
      isHardwareBlocklisted() && useAec3 -> AudioProcessingMethod.ForceSoftwareAec3
      isHardwareBlocklisted() -> AudioProcessingMethod.ForceSoftwareAecM
      isSoftwareBlocklisted() -> AudioProcessingMethod.ForceHardware
      Build.VERSION.SDK_INT < 29 && FeatureFlags.useHardwareAecIfOlderThanApi29() -> AudioProcessingMethod.ForceHardware
      Build.VERSION.SDK_INT < 29 && useAec3 -> AudioProcessingMethod.ForceSoftwareAec3
      Build.VERSION.SDK_INT < 29 -> AudioProcessingMethod.ForceSoftwareAecM
      else -> AudioProcessingMethod.ForceHardware
    }
  }

  fun isTelecomAllowedForDevice(): Boolean {
    return FeatureFlags.telecomManufacturerAllowList().lowercase().asListContains(Build.MANUFACTURER.lowercase()) &&
      !FeatureFlags.telecomModelBlockList().lowercase().asListContains(Build.MODEL.lowercase())
  }

  private fun isHardwareBlocklisted(): Boolean {
    return FeatureFlags.hardwareAecBlocklistModels().asListContains(Build.MODEL)
  }

  private fun isSoftwareBlocklisted(): Boolean {
    return FeatureFlags.softwareAecBlocklistModels().asListContains(Build.MODEL)
  }
}
