package org.thoughtcrime.securesms.megaphone

import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * Calculates if the verify key megaphone should be shown based on the following rules
 * - 1 reminder within 14 days of creation, every 6 months after that
 * - Allow snooze only once, for a week
 * - Do not show within 1 week of showing the PIN reminder
 */
class VerifyBackupKeyReminderSchedule : MegaphoneSchedule {

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    if (!SignalStore.backup.areBackupsEnabled) {
      return false
    }

    val lastVerifiedTime = SignalStore.backup.lastVerifyKeyTime
    val previouslySnoozed = SignalStore.backup.hasSnoozedVerified
    val isFirstReminder = !SignalStore.backup.hasVerifiedBefore

    val intervalTime = if (isFirstReminder) 14.days.inWholeMilliseconds else 183.days.inWholeMilliseconds
    val snoozedTime = if (previouslySnoozed) 7.days.inWholeMilliseconds else 0.days.inWholeMilliseconds

    val shouldShowBackupKeyReminder = System.currentTimeMillis() > (lastVerifiedTime + intervalTime + snoozedTime)
    val hasShownPinReminderRecently = System.currentTimeMillis() < SignalStore.pin.lastReminderTime + 7.days.inWholeMilliseconds

    return shouldShowBackupKeyReminder && !hasShownPinReminderRecently
  }
}
