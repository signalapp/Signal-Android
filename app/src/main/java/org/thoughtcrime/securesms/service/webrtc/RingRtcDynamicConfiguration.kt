package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.signal.core.util.asListContains
import org.signal.ringrtc.CallManager.AudioProcessingMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Utility class to determine which AEC method RingRTC should use.
 */
object RingRtcDynamicConfiguration {

  private val KNOWN_ISSUE_ROMS = "(lineage|calyxos)".toRegex(RegexOption.IGNORE_CASE)

  @JvmStatic
  fun getAudioProcessingMethod(): AudioProcessingMethod {
    if (SignalStore.internal.callingAudioProcessingMethod() != AudioProcessingMethod.Default) {
      return SignalStore.internal.callingAudioProcessingMethod()
    }

    return if (shouldUseOboeAdm()) {
      when {
        shouldUseSoftwareAecForOboe() -> AudioProcessingMethod.ForceSoftwareAec3
        else -> AudioProcessingMethod.ForceHardware
      }
    } else {
      when {
        isHardwareBlocklisted() || isKnownFaultyHardwareImplementation() -> AudioProcessingMethod.ForceSoftwareAec3
        isSoftwareBlocklisted() -> AudioProcessingMethod.ForceHardware
        Build.VERSION.SDK_INT < 29 && RemoteConfig.useHardwareAecIfOlderThanApi29 -> AudioProcessingMethod.ForceHardware
        Build.VERSION.SDK_INT < 29 -> AudioProcessingMethod.ForceSoftwareAec3
        else -> AudioProcessingMethod.ForceHardware
      }
    }
  }

  fun isTelecomAllowedForDevice(): Boolean {
    return RemoteConfig.telecomManufacturerAllowList.lowercase().asListContains(Build.MANUFACTURER.lowercase()) &&
      !RemoteConfig.telecomModelBlocklist.lowercase().asListContains(Build.MODEL.lowercase())
  }

  private fun isHardwareBlocklisted(): Boolean {
    return RemoteConfig.hardwareAecBlocklistModels.asListContains(Build.MODEL)
  }

  private fun isKnownFaultyHardwareImplementation(): Boolean {
    return Build.PRODUCT.contains(KNOWN_ISSUE_ROMS) ||
      Build.DISPLAY.contains(KNOWN_ISSUE_ROMS) ||
      Build.HOST.contains(KNOWN_ISSUE_ROMS)
  }

  private fun isSoftwareBlocklisted(): Boolean {
    return RemoteConfig.softwareAecBlocklistModels.asListContains(Build.MODEL)
  }

  @JvmStatic
  fun shouldUseOboeAdm(): Boolean {
    if (RemoteConfig.internalUser) {
      return SignalStore.internal.callingEnableOboeAdm()
    }

    // For now, only allow the Oboe ADM to be used for custom ROMS.
    return RemoteConfig.oboeDeployment && isKnownFaultyHardwareImplementation() && !shouldUseJavaAdm()
  }

  private fun shouldUseJavaAdm(): Boolean {
    return RemoteConfig.useJavaAdmModels.asListContains(Build.MODEL)
  }

  private fun shouldUseSoftwareAecForOboe(): Boolean {
    return RemoteConfig.useSoftwareAecForOboeModels.asListContains(Build.MODEL)
  }
}
