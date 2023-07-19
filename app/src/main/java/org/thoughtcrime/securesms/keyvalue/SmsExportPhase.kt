package org.thoughtcrime.securesms.keyvalue

enum class SmsExportPhase(val duration: Long) {
  PHASE_3(0);

  fun allowSmsFeatures(): Boolean {
    return false
  }

  fun isSmsSupported(): Boolean {
    return false
  }

  fun isBlockingUi(): Boolean {
    return true
  }

  companion object {
    @JvmStatic
    fun getCurrentPhase(): SmsExportPhase {
      return PHASE_3
    }
  }
}
