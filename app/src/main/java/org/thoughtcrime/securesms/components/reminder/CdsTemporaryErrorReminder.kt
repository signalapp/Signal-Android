package org.thoughtcrime.securesms.components.reminder

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Reminder shown when CDS is rate-limited, preventing us from temporarily doing a refresh.
 */
class CdsTemporaryErrorReminder : Reminder(R.string.reminder_cds_warning_body) {

  init {
    addAction(
      Action(
        R.string.reminder_cds_warning_learn_more,
        R.id.reminder_action_cds_temporary_error_learn_more
      )
    )
  }

  override fun isDismissable(): Boolean {
    return false
  }

  override fun getImportance(): Importance {
    return Importance.ERROR
  }

  companion object {
    @JvmStatic
    fun isEligible(): Boolean {
      val timeUntilUnblock = SignalStore.misc.cdsBlockedUtil - System.currentTimeMillis()
      return SignalStore.misc.isCdsBlocked && timeUntilUnblock < CdsPermanentErrorReminder.PERMANENT_TIME_CUTOFF
    }
  }
}
