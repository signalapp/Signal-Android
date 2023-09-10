package org.thoughtcrime.securesms.components.reminder

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags

/**
 * Displays a reminder message when the local username gets out of sync with
 * what the server thinks our username is.
 */
class UsernameOutOfSyncReminder : Reminder(R.string.UsernameOutOfSyncReminder__something_went_wrong) {

  init {
    addAction(
      Action(
        R.string.UsernameOutOfSyncReminder__fix_now,
        R.id.reminder_action_fix_username
      )
    )
  }

  override fun isDismissable(): Boolean {
    return false
  }

  companion object {
    @JvmStatic
    fun isEligible(): Boolean {
      return FeatureFlags.usernames() && SignalStore.account().usernameOutOfSync
    }
  }
}
