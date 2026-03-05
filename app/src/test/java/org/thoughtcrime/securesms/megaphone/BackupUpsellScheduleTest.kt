package org.thoughtcrime.securesms.megaphone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import org.thoughtcrime.securesms.megaphone.Megaphones.Event
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class BackupUpsellScheduleTest {

  private val now = System.currentTimeMillis()

  // -- First display (seenCount = 0) --

  @Test
  fun `shows on first display when no other backup upsell seen`() {
    val schedule = schedule(emptyRecords())
    assertTrue(schedule.shouldDisplay(0, 0, 0, now))
  }

  @Test
  fun `shows on first display when other upsell was seen outside the threshold`() {
    val records = emptyRecords().toMutableMap().apply {
      put(Event.BACKUPS_GENERIC_UPSELL, record(Event.BACKUPS_GENERIC_UPSELL, lastSeen = now - 60.days.inWholeMilliseconds - 1))
    }
    val schedule = schedule(records)
    assertTrue(schedule.shouldDisplay(0, 0, 0, now))
  }

  @Test
  fun `suppressed on first display when another upsell was seen within the threshold`() {
    val records = emptyRecords().toMutableMap().apply {
      put(Event.BACKUPS_GENERIC_UPSELL, record(Event.BACKUPS_GENERIC_UPSELL, lastSeen = now - 60.days.inWholeMilliseconds + 1.hours.inWholeMilliseconds))
    }
    val schedule = schedule(records)
    assertFalse(schedule.shouldDisplay(0, 0, 0, now))
  }

  // -- Recurring gap logic (seenCount > 0) --

  @Test
  fun `shows after gap elapsed since last seen`() {
    val gapMs = 60.days.inWholeMilliseconds
    val schedule = schedule(emptyRecords(), gapMs)
    assertTrue(schedule.shouldDisplay(1, now - gapMs - 1, 0, now))
  }

  @Test
  fun `suppressed when gap has not elapsed since last seen`() {
    val gapMs = 60.days.inWholeMilliseconds
    val schedule = schedule(emptyRecords(), gapMs)
    assertFalse(schedule.shouldDisplay(1, now - gapMs + 1.hours.inWholeMilliseconds, 0, now))
  }

  @Test
  fun `uses second gap for second snooze`() {
    val firstGap = 60.days.inWholeMilliseconds
    val secondGap = 120.days.inWholeMilliseconds
    val schedule = schedule(emptyRecords(), firstGap, secondGap)

    // seenCount=2 -> uses gaps[1] = 120 days
    assertFalse(schedule.shouldDisplay(2, now - 100.days.inWholeMilliseconds, 0, now))
    assertTrue(schedule.shouldDisplay(2, now - secondGap - 1, 0, now))
  }

  @Test
  fun `repeats last gap for high seen counts`() {
    val firstGap = 60.days.inWholeMilliseconds
    val secondGap = 120.days.inWholeMilliseconds
    val schedule = schedule(emptyRecords(), firstGap, secondGap)

    // seenCount=5 -> clamps to gaps[1] = 120 days
    assertFalse(schedule.shouldDisplay(5, now - 100.days.inWholeMilliseconds, 0, now))
    assertTrue(schedule.shouldDisplay(5, now - secondGap - 1, 0, now))
  }

  // -- Combined: cross-event snooze AND recurring gap --

  @Test
  fun `cross-event snooze blocks even when recurring gap is satisfied`() {
    val gapMs = 60.days.inWholeMilliseconds
    val records = emptyRecords().toMutableMap().apply {
      put(Event.BACKUP_LOW_STORAGE_UPSELL, record(Event.BACKUP_LOW_STORAGE_UPSELL, lastSeen = now - 30.days.inWholeMilliseconds))
    }
    val schedule = schedule(records, gapMs)

    // Own gap satisfied (last seen 90 days ago) but another upsell was seen 30 days ago
    assertFalse(schedule.shouldDisplay(1, now - 90.days.inWholeMilliseconds, 0, now))
  }

  @Test
  fun `shows when both cross-event snooze and recurring gap are satisfied`() {
    val gapMs = 60.days.inWholeMilliseconds
    val records = emptyRecords().toMutableMap().apply {
      put(Event.BACKUP_LOW_STORAGE_UPSELL, record(Event.BACKUP_LOW_STORAGE_UPSELL, lastSeen = now - 60.days.inWholeMilliseconds - 1))
    }
    val schedule = schedule(records, gapMs)
    assertTrue(schedule.shouldDisplay(1, now - gapMs - 1, 0, now))
  }

  // -- Ignores non-backup events --

  @Test
  fun `ignores non-backup upsell events in cross-event check`() {
    val records = emptyRecords().toMutableMap().apply {
      put(Event.PIN_REMINDER, record(Event.PIN_REMINDER, lastSeen = now))
      put(Event.NOTIFICATIONS, record(Event.NOTIFICATIONS, lastSeen = now))
    }
    val schedule = schedule(records)
    assertTrue(schedule.shouldDisplay(0, 0, 0, now))
  }

  // -- Helpers --

  private fun schedule(records: Map<Event, MegaphoneRecord>, vararg gaps: Long): BackupUpsellSchedule {
    return BackupUpsellSchedule(records, *gaps)
  }

  private fun record(event: Event, seenCount: Int = 1, lastSeen: Long = 0, firstVisible: Long = 0, finished: Boolean = false): MegaphoneRecord {
    return MegaphoneRecord(event, seenCount, lastSeen, firstVisible, finished)
  }

  private fun emptyRecords(): Map<Event, MegaphoneRecord> {
    return BackupUpsellSchedule.BACKUP_UPSELL_EVENTS.associateWith { record(it, seenCount = 0) }
  }
}
