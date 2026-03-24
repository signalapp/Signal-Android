package org.thoughtcrime.securesms.megaphone

import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import kotlin.time.Duration.Companion.days

/**
 * Schedule for backup upsell megaphones that combines:
 * 1. Per-megaphone recurring display timing (like [RecurringSchedule])
 * 2. Cross-megaphone shared snooze: if any other backup upsell was seen recently, suppress display
 *
 * @param records   All megaphone records, used to find the most recent lastSeen across backup upsell events.
 * @param gaps      Per-megaphone recurring gaps, same semantics as [RecurringSchedule].
 */
class BackupUpsellSchedule(
  private val records: Map<Megaphones.Event, MegaphoneRecord>,
  vararg val gaps: Long
) : MegaphoneSchedule {

  companion object {
    @JvmField
    val BACKUP_UPSELL_EVENTS: Set<Megaphones.Event> = setOf(
      Megaphones.Event.BACKUPS_GENERIC_UPSELL,
      Megaphones.Event.BACKUP_MESSAGE_COUNT_UPSELL,
      Megaphones.Event.BACKUP_MEDIA_SIZE_UPSELL,
      Megaphones.Event.BACKUP_LOW_STORAGE_UPSELL
    )

    @JvmField
    val MIN_TIME_BETWEEN_BACKUP_UPSELLS: Long = 60.days.inWholeMilliseconds
  }

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    val lastSeen = lastSeen.coerceAtMost(currentTime)

    val lastSeenAnyBackupUpsell: Long = records.entries
      .filter { it.key in BACKUP_UPSELL_EVENTS }
      .mapNotNull { it.value.lastSeen.takeIf { t -> t > 0 } }
      .maxOrNull() ?: 0L

    if (currentTime - lastSeenAnyBackupUpsell <= MIN_TIME_BETWEEN_BACKUP_UPSELLS) {
      return false
    }

    if (seenCount == 0) {
      return true
    }

    val gap = gaps[minOf(seenCount - 1, gaps.size - 1)]
    return lastSeen + gap <= currentTime
  }
}
