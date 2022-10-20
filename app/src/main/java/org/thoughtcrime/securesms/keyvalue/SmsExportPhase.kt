package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.Util
import kotlin.time.Duration.Companion.days

enum class SmsExportPhase(val duration: Long) {
  PHASE_0(-1),
  PHASE_1(0.days.inWholeMilliseconds),
  PHASE_2(45.days.inWholeMilliseconds),
  PHASE_3(105.days.inWholeMilliseconds);

  fun allowSmsFeatures(): Boolean {
    return this == PHASE_0 || (Util.isDefaultSmsProvider(ApplicationDependencies.getApplication()) && SignalStore.misc().smsExportPhase.isSmsSupported())
  }

  fun isSmsSupported(): Boolean {
    return this != PHASE_3
  }

  fun isFullscreen(): Boolean {
    return this.ordinal > PHASE_1.ordinal
  }

  fun isBlockingUi(): Boolean {
    return this == PHASE_3
  }

  fun isAtLeastPhase1(): Boolean {
    return this.ordinal >= PHASE_1.ordinal
  }

  companion object {
    @JvmStatic
    fun getCurrentPhase(duration: Long): SmsExportPhase {
      return values().findLast { duration >= it.duration }!!
    }
  }
}
