package org.thoughtcrime.securesms.notifications.profiles

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.BeforeClass
import org.junit.Test
import org.thoughtcrime.securesms.util.toMillis
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

class NotificationProfileScheduleTest {
  private val sunday0am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 0, 0, 0)
  private val sunday1am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 1, 0, 0)
  private val sunday9am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 9, 0, 0)
  private val sunday930am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 9, 30, 0)
  private val sunday10pm: LocalDateTime = LocalDateTime.of(2021, 7, 4, 22, 0, 0)

  private val monday0am: LocalDateTime = sunday0am.plusDays(1)
  private val monday1am: LocalDateTime = sunday1am.plusDays(1)
  private val monday9am: LocalDateTime = sunday9am.plusDays(1)
  private val monday10pm: LocalDateTime = sunday10pm.plusDays(1)

  private val tuesday1am: LocalDateTime = sunday1am.plusDays(2)
  private val tuesday9am: LocalDateTime = sunday9am.plusDays(2)
  private val tuesday10pm: LocalDateTime = sunday10pm.plusDays(2)

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
  }

  @Test
  fun `when time is within enabled schedule 9am to 5pm then return true`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 900, end = 1700, daysEnabled = setOf(DayOfWeek.SUNDAY))

    assertThat(schedule.isCurrentlyActive(sunday9am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday9am.plusHours(1).toMillis(ZoneOffset.UTC))).isTrue()
  }

  @Test
  fun `when time is outside enabled schedule 9am to 5pm then return false`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 900, end = 1700, daysEnabled = setOf(DayOfWeek.SUNDAY))

    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(sunday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday9am.toMillis(ZoneOffset.UTC))).isFalse()
  }

  @Test
  fun `when time is inside enabled with day wrapping schedule 10pm to 2am then return true`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 2100, end = 200, daysEnabled = setOf(DayOfWeek.MONDAY))

    assertThat(schedule.isCurrentlyActive(monday10pm.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(tuesday1am.toMillis(ZoneOffset.UTC))).isTrue()
  }

  @Test
  fun `when time is outside enabled with day wrapping schedule 10pm to 2am then return false`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 2100, end = 200, daysEnabled = setOf(DayOfWeek.MONDAY))

    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(sunday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday10pm.toMillis(ZoneOffset.UTC))).isFalse()
  }

  @Test
  fun `when time is inside enabled schedule 12am to 10am then return true`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 0, end = 1000, daysEnabled = setOf(DayOfWeek.SUNDAY))

    assertThat(schedule.isCurrentlyActive(sunday0am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday9am.toMillis(ZoneOffset.UTC))).isTrue()
  }

  @Test
  fun `when time is inside enabled schedule 12am to 12am then return true`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 0, end = 0, daysEnabled = setOf(DayOfWeek.SUNDAY))

    assertThat(schedule.isCurrentlyActive(sunday0am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday9am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday10pm.toMillis(ZoneOffset.UTC))).isTrue()
  }

  @Test
  fun `when time is outside enabled schedule 12am to 12am then return false`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 0, end = 0, daysEnabled = setOf(DayOfWeek.SUNDAY))

    assertThat(schedule.isCurrentlyActive(monday0am.plusMinutes(1).toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday10pm.toMillis(ZoneOffset.UTC))).isFalse()
  }

  @Test
  fun `when enabled schedule 12am to 12am for all days then return true`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 0, end = 0, daysEnabled = DayOfWeek.entries.toSet())

    assertThat(schedule.isCurrentlyActive(sunday0am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday9am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(sunday10pm.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(monday0am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(monday1am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(monday9am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(monday10pm.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(tuesday1am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(tuesday9am.toMillis(ZoneOffset.UTC))).isTrue()
    assertThat(schedule.isCurrentlyActive(tuesday10pm.toMillis(ZoneOffset.UTC))).isTrue()
  }

  @Test
  fun `when disabled schedule 12am to 12am for all days then return false`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = false, start = 0, end = 0, daysEnabled = DayOfWeek.entries.toSet())

    assertThat(schedule.isCurrentlyActive(sunday0am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(sunday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(sunday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(sunday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday0am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(monday10pm.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday1am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday9am.toMillis(ZoneOffset.UTC))).isFalse()
    assertThat(schedule.isCurrentlyActive(tuesday10pm.toMillis(ZoneOffset.UTC))).isFalse()
  }

  @Test
  fun `when end time is midnight return midnight of next day from now`() {
    val schedule = NotificationProfileSchedule(id = 1L, enabled = false, start = 0, end = 0, daysEnabled = DayOfWeek.entries.toSet())
    assertThat(schedule.endDateTime(sunday930am)).isEqualTo(monday0am)
  }
}
