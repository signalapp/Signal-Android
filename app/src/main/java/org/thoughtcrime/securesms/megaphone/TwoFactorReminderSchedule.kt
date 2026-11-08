/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * Decides when to remind a phone-numberless user to verify one of their two-factor methods:
 * - Only when the account is known to have a method
 * - First 14 days after the account was first seen to have one, then every 6 months after each verification
 * - Snoozable only once per reminder, for a week
 */
class TwoFactorReminderSchedule : MegaphoneSchedule {

  companion object {
    /**
     * Records what the service says the account has. Methods are verified when they're added, so learning of them
     * for the first time starts the clock on the first reminder.
     */
    @JvmStatic
    fun onMethodsObserved(hasMethods: Boolean) {
      if (hasMethods == SignalStore.account.hasTwoFactorMethods) {
        return
      }

      SignalStore.account.hasTwoFactorMethods = hasMethods
      SignalStore.uiHints.twoFactorLastVerifiedTime = System.currentTimeMillis()
      SignalStore.uiHints.setHasVerifiedTwoFactorBefore(false)
      SignalStore.uiHints.setHasSnoozedTwoFactorReminder(false)
    }

    /** Records that the user just verified one of their methods through the reminder. */
    @JvmStatic
    fun onVerified() {
      SignalStore.account.hasTwoFactorMethods = true
      SignalStore.uiHints.twoFactorLastVerifiedTime = System.currentTimeMillis()
      SignalStore.uiHints.setHasVerifiedTwoFactorBefore(true)
      SignalStore.uiHints.setHasSnoozedTwoFactorReminder(false)
    }
  }

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    if (!SignalStore.account.isPhoneNumberless) {
      return false
    }

    if (!SignalStore.account.hasTwoFactorMethods) {
      return false
    }

    val interval = if (SignalStore.uiHints.hasVerifiedTwoFactorBefore()) 183.days else 14.days

    val intervalHasPassed = currentTime > SignalStore.uiHints.twoFactorLastVerifiedTime + interval.inWholeMilliseconds
    val snoozeHasExpired = !SignalStore.uiHints.hasSnoozedTwoFactorReminder() || currentTime > lastSeen + 7.days.inWholeMilliseconds

    return intervalHasPassed && snoozeHasExpired
  }
}
