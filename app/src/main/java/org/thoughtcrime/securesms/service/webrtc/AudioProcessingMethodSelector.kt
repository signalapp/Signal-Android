package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import androidx.annotation.VisibleForTesting
import org.signal.ringrtc.CallManager.AudioProcessingMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags

/**
 * Utility class to determine which AEC method RingRTC should use.
 */
object AudioProcessingMethodSelector {

  @JvmStatic
  fun get(): AudioProcessingMethod {
    if (SignalStore.internalValues().audioProcessingMethod() != AudioProcessingMethod.Default) {
      return SignalStore.internalValues().audioProcessingMethod()
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

  private fun isHardwareBlocklisted(): Boolean {
    return modelInList(Build.MODEL, FeatureFlags.hardwareAecBlocklistModels())
  }

  private fun isSoftwareBlocklisted(): Boolean {
    return modelInList(Build.MODEL, FeatureFlags.softwareAecBlocklistModels())
  }

  @VisibleForTesting
  fun modelInList(model: String, serializedList: String): Boolean {
    val items: List<String> = serializedList
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toList()

    val exactMatches = items.filter { it.last() != '*' }
    val prefixMatches = items.filter { it.last() == '*' }

    return exactMatches.contains(model) ||
      prefixMatches
        .map { it.substring(0, it.length - 1) }
        .any { model.startsWith(it) }
  }
}
