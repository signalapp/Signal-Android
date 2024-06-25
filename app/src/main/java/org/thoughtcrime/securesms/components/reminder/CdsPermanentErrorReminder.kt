package org.thoughtcrime.securesms.components.reminder

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * Reminder shown when CDS is in a permanent error state, preventing us from doing a sync.
 */
class CdsPermanentErrorReminder : Reminder(R.string.reminder_cds_permanent_error_body) {

  init {
    addAction(
      Action(
        R.string.reminder_cds_permanent_error_learn_more,
        R.id.reminder_action_cds_permanent_error_learn_more
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
    /**
     * Even if we're not truly "permanently blocked", if the time until we're unblocked is long enough, we'd rather show the permanent error message than
     * telling the user to wait for 3 months or something.
     */
    val PERMANENT_TIME_CUTOFF = 30.days.inWholeMilliseconds

    @JvmStatic
    fun isEligible(): Boolean {
      val timeUntilUnblock = SignalStore.misc.cdsBlockedUtil - System.currentTimeMillis()
      return SignalStore.misc.isCdsBlocked && timeUntilUnblock >= PERMANENT_TIME_CUTOFF
    }
  }
}
