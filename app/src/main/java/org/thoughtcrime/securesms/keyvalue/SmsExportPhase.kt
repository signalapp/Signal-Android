package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.Util
import kotlin.time.Duration.Companion.days

enum class SmsExportPhase(val duration: Long) {
  PHASE_2(0.days.inWholeMilliseconds),
  PHASE_3(51.days.inWholeMilliseconds);

  fun allowSmsFeatures(): Boolean {
    return Util.isDefaultSmsProvider(ApplicationDependencies.getApplication()) && SignalStore.misc().smsExportPhase.isSmsSupported()
  }

  fun isSmsSupported(): Boolean {
    return this != PHASE_3
  }

  fun isBlockingUi(): Boolean {
    return this == PHASE_3
  }

  companion object {
    @JvmStatic
    fun getCurrentPhase(duration: Long): SmsExportPhase {
      return values().findLast { duration >= it.duration }!!
    }
  }
}
