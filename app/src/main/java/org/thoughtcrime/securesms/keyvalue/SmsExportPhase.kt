package org.thoughtcrime.securesms.keyvalue

import kotlin.time.Duration.Companion.days

enum class SmsExportPhase(val duration: Long) {
  PHASE_1(0.days.inWholeMilliseconds),
  PHASE_2(45.days.inWholeMilliseconds),
  PHASE_3(105.days.inWholeMilliseconds);

  fun isSmsSupported(): Boolean {
    return this != PHASE_3
  }

  fun isFullscreen(): Boolean {
    return this != PHASE_1
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
