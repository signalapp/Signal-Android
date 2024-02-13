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

  private val KNOWN_ISSUE_ROMS = "(lineage|calyxos)".toRegex(RegexOption.IGNORE_CASE)

  @JvmStatic
  fun getAudioProcessingMethod(): AudioProcessingMethod {
    if (SignalStore.internalValues().callingAudioProcessingMethod() != AudioProcessingMethod.Default) {
      return SignalStore.internalValues().callingAudioProcessingMethod()
    }

    return when {
      isHardwareBlocklisted() || isKnownFaultyHardwareImplementation() -> AudioProcessingMethod.ForceSoftwareAec3
      isSoftwareBlocklisted() -> AudioProcessingMethod.ForceHardware
      Build.VERSION.SDK_INT < 29 && FeatureFlags.useHardwareAecIfOlderThanApi29() -> AudioProcessingMethod.ForceHardware
      Build.VERSION.SDK_INT < 29 -> AudioProcessingMethod.ForceSoftwareAec3
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

  fun isKnownFaultyHardwareImplementation(): Boolean {
    return Build.PRODUCT.contains(KNOWN_ISSUE_ROMS) ||
      Build.DISPLAY.contains(KNOWN_ISSUE_ROMS) ||
      Build.HOST.contains(KNOWN_ISSUE_ROMS)
  }

  private fun isSoftwareBlocklisted(): Boolean {
    return FeatureFlags.softwareAecBlocklistModels().asListContains(Build.MODEL)
  }
}
