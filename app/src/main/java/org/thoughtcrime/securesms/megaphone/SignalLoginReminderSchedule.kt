/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * Decides when to remind a phone-numberless user to prove they still know their Signal Login:
 * - Only for phone-numberless accounts, which are the only ones with a Signal Login
 * - Only once the reminder clock has started, either at login or by [org.thoughtcrime.securesms.migrations.SignalLoginReminderMigrationJob]
 * - First 14 days after the clock started, then every 6 months after each verification
 * - Snoozable only once per reminder, for a week
 */
class SignalLoginReminderSchedule : MegaphoneSchedule {

  companion object {
    /**
     * Starts the clock on the first reminder if nothing has yet, which covers accounts that registered before the app
     * started tracking this.
     */
    @JvmStatic
    fun startReminderClockIfNeeded() {
      if (SignalStore.uiHints.signalLoginLastVerifiedTime == 0L) {
        onLoggedIn()
      }
    }

    /** Records that the user just registered or logged in, which proves they know their login as of now. */
    @JvmStatic
    fun onLoggedIn() {
      SignalStore.uiHints.signalLoginLastVerifiedTime = System.currentTimeMillis()
      SignalStore.uiHints.setHasVerifiedSignalLoginBefore(false)
      SignalStore.uiHints.setHasSnoozedSignalLoginReminder(false)
    }

    /** Records that the user just verified their login through the reminder. */
    @JvmStatic
    fun onVerified() {
      SignalStore.uiHints.signalLoginLastVerifiedTime = System.currentTimeMillis()
      SignalStore.uiHints.setHasVerifiedSignalLoginBefore(true)
      SignalStore.uiHints.setHasSnoozedSignalLoginReminder(false)
    }
  }

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    if (!SignalStore.account.isPhoneNumberless) {
      return false
    }

    if (SignalStore.uiHints.signalLoginLastVerifiedTime == 0L) {
      return false
    }

    val interval = if (SignalStore.uiHints.hasVerifiedSignalLoginBefore()) 183.days else 14.days

    val intervalHasPassed = currentTime > SignalStore.uiHints.signalLoginLastVerifiedTime + interval.inWholeMilliseconds
    val snoozeHasExpired = !SignalStore.uiHints.hasSnoozedSignalLoginReminder() || currentTime > lastSeen + 7.days.inWholeMilliseconds

    return intervalHasPassed && snoozeHasExpired
  }
}
