package org.thoughtcrime.securesms.service.webrtc

import android.os.Build
import org.signal.ringrtc.CallManager.AudioProcessingMethod
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags

/**
 * Utility class to determine which AEC method RingRTC should use.
 */
object AudioProcessingMethodSelector {

  private val hardwareModels: Set<String> by lazy {
    FeatureFlags.hardwareAecModels()
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()
  }

  @JvmStatic
  fun get(): AudioProcessingMethod {
    if (SignalStore.internalValues().audioProcessingMethod() != AudioProcessingMethod.Default) {
      return SignalStore.internalValues().audioProcessingMethod()
    }

    return when {
      FeatureFlags.forceDefaultAec() -> AudioProcessingMethod.Default
      hardwareModels.contains(Build.MODEL) -> AudioProcessingMethod.ForceHardware
      else -> AudioProcessingMethod.ForceSoftware
    }
  }
}
