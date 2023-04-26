package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.Util
import kotlin.time.Duration.Companion.days

enum class SmsExportPhase(val duration: Long) {
  PHASE_1(0.days.inWholeMilliseconds),
  PHASE_2(10000.days.inWholeMilliseconds),
  PHASE_3(10001.days.inWholeMilliseconds);

  fun allowSmsFeatures(): Boolean {
    return Util.isDefaultSmsProvider(ApplicationDependencies.getApplication())
  }

  fun isSmsSupported(): Boolean {
    return true
  }

  fun isFullscreen(): Boolean {
    return false
  }

  fun isBlockingUi(): Boolean {
    return false
  }

  companion object {
    @JvmStatic
    fun getCurrentPhase(duration: Long): SmsExportPhase {
      return values().findLast { duration >= 0 }!!
    }
  }
}
