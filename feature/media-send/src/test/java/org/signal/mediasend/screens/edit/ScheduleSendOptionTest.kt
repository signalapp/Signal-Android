/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Covers the suggested times [ScheduleSendOption.forCurrentTime] offers, which are what the schedule menu is built
 * from. Times are computed in a fixed zone so that the expectations hold wherever the tests run.
 */
class ScheduleSendOptionTest {

  @Test
  fun `Given a mid-morning weekday, when building options, then the rest of today is suggested soonest-last`() {
    val options = optionsAt(WEDNESDAY.atTime(9, 30))

    assertEquals(
      listOf(
        ScheduleSendOption.PickTime,
        presetAt(WEDNESDAY.atTime(21, 0)),
        presetAt(WEDNESDAY.atTime(18, 0)),
        presetAt(WEDNESDAY.atTime(12, 0))
      ),
      options
    )
  }

  @Test
  fun `Given a time past the last suggestion of the day, when building options, then tomorrow is suggested`() {
    val options = optionsAt(WEDNESDAY.atTime(22, 15))

    assertEquals(
      listOf(
        ScheduleSendOption.PickTime,
        presetAt(THURSDAY.atTime(18, 0)),
        presetAt(THURSDAY.atTime(12, 0)),
        presetAt(THURSDAY.atTime(8, 0))
      ),
      options
    )
  }

  @Test
  fun `Given the suggestions roll into the next day, when building options, then the following day's hours are used`() {
    val options = optionsAt(WEDNESDAY.atTime(19, 0))

    assertEquals(
      listOf(
        ScheduleSendOption.PickTime,
        presetAt(THURSDAY.atTime(12, 0)),
        presetAt(THURSDAY.atTime(8, 0)),
        presetAt(WEDNESDAY.atTime(21, 0))
      ),
      options
    )
  }

  @Test
  fun `Given a Friday, when building options, then Monday morning is also suggested`() {
    val options = optionsAt(FRIDAY.atTime(9, 0))

    assertEquals(
      listOf(
        ScheduleSendOption.PickTime,
        presetAt(MONDAY.atTime(8, 0)),
        presetAt(FRIDAY.atTime(21, 0)),
        presetAt(FRIDAY.atTime(18, 0)),
        presetAt(FRIDAY.atTime(12, 0))
      ),
      options
    )
  }

  @Test
  fun `Given a Saturday, when building options, then Monday morning is also suggested`() {
    val options = optionsAt(SATURDAY.atTime(9, 0))

    assertEquals(
      listOf(
        ScheduleSendOption.PickTime,
        presetAt(MONDAY.atTime(8, 0)),
        presetAt(SATURDAY.atTime(21, 0)),
        presetAt(SATURDAY.atTime(18, 0)),
        presetAt(SATURDAY.atTime(12, 0))
      ),
      options
    )
  }

  @Test
  fun `Given any time, when building options, then every suggestion is in the future and on the minute`() {
    val now = WEDNESDAY.atTime(11, 47, 32)

    val presets = optionsAt(now).filterIsInstance<ScheduleSendOption.PresetTime>()

    assertEquals(3, presets.size)
    presets.forEach { preset ->
      val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(preset.timeMs), ZONE)
      assertEquals(0, dateTime.minute)
      assertEquals(0, dateTime.second)
      assert(preset.timeMs > now.toEpochMillis()) { "$dateTime is not after $now" }
    }
  }

  private fun optionsAt(dateTime: LocalDateTime): List<ScheduleSendOption> {
    return ScheduleSendOption.forCurrentTime(dateTime.toEpochMillis(), ZONE)
  }

  private fun presetAt(dateTime: LocalDateTime): ScheduleSendOption.PresetTime {
    return ScheduleSendOption.PresetTime(dateTime.toEpochMillis())
  }

  private fun LocalDateTime.toEpochMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

  companion object {
    private val ZONE: ZoneId = ZoneOffset.UTC

    private val WEDNESDAY = LocalDate.of(2026, 7, 1)
    private val THURSDAY = LocalDate.of(2026, 7, 2)
    private val FRIDAY = LocalDate.of(2026, 7, 3)
    private val SATURDAY = LocalDate.of(2026, 7, 4)
    private val MONDAY = LocalDate.of(2026, 7, 6)
  }
}
