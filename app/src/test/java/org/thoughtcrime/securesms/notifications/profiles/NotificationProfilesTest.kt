package org.thoughtcrime.securesms.notifications.profiles

import android.app.Application
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.SignalStoreRule
import org.thoughtcrime.securesms.keyvalue.NotificationProfileValues
import org.thoughtcrime.securesms.util.toMillis
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NotificationProfilesTest {

  private val sunday830am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 8, 30, 0)
  private val sunday9am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 9, 0, 0)
  private val sunday930am: LocalDateTime = LocalDateTime.of(2021, 7, 4, 9, 30, 0)
  private val monday830am: LocalDateTime = sunday830am.plusDays(1)
  private val utc: ZoneId = ZoneId.of("UTC")

  private val first = NotificationProfile(
    id = 1L,
    "first",
    "",
    createdAt = 1000L,
    schedule = NotificationProfileSchedule(1)
  )

  private val second = NotificationProfile(
    id = 2L,
    "second",
    "",
    createdAt = 2000L,
    schedule = NotificationProfileSchedule(2)
  )

  @get:Rule
  val signalStore: SignalStoreRule = SignalStoreRule()

  @Test
  fun `when no profiles then return null`() {
    assertThat("no active profile", NotificationProfiles.getActiveProfile(emptyList(), 1000L, utc), nullValue())
  }

  @Test
  fun `when no manually enabled or schedule profiles then return null`() {
    val profiles = listOf(first, second)
    assertThat("no active profile", NotificationProfiles.getActiveProfile(profiles, 3000L, utc), nullValue())
  }

  @Test
  fun `when first is not enabled and second is manually enabled forever then return second`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, second.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, Long.MAX_VALUE)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, 5000L)

    val profiles = listOf(first, second)
    assertThat("active profile is profile second", NotificationProfiles.getActiveProfile(profiles, 3000L, utc), `is`(profiles[1]))
  }

  @Test
  fun `when first is scheduled and second is not manually enabled and now is within schedule return first`() {
    val schedule = NotificationProfileSchedule(id = 3L, true, start = 700, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = schedule), second)
    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[0]))
  }

  @Test
  fun `when first is scheduled and second is manually enabled forever within first's schedule then return second`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, second.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, Long.MAX_VALUE)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, true, start = 700, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = schedule), second)
    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[1]))
  }

  @Test
  fun `when first is scheduled and second is manually enabled forever before first's schedule start then return first`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, second.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, Long.MAX_VALUE)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, true, start = 900, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = schedule), second)

    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, sunday930am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[0]))
  }

  @Test
  fun `when first and second have overlapping schedules and second is created after first and now is within both then return second`() {
    val firstSchedule = NotificationProfileSchedule(id = 3L, true, start = 700, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val secondSchedule = NotificationProfileSchedule(id = 4L, true, start = 800, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = firstSchedule), second.copy(schedule = secondSchedule))

    assertThat("active profile is second", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[1]))
  }

  @Test
  fun `when first and second have overlapping schedules and first is created before second and first is manually enabled within overlapping schedule then return first`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, first.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, Long.MAX_VALUE)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val firstSchedule = NotificationProfileSchedule(id = 3L, true, start = 700, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val secondSchedule = NotificationProfileSchedule(id = 4L, true, start = 700, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = firstSchedule), second.copy(schedule = secondSchedule))

    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[0]))
  }

  @Test
  fun `when profile is manually enabled for set time after schedule end and now is after schedule end but before manual then return profile`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, first.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, sunday930am.toMillis(ZoneOffset.UTC))
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, true, start = 700, end = 845, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = schedule))
    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[0]))
  }

  @Test
  fun `when profile is manually enabled for set time before schedule end and now is after manual but before schedule end then return null`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, first.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, sunday9am.toMillis(ZoneOffset.UTC))
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, true, start = 700, end = 1000, daysEnabled = setOf(DayOfWeek.SUNDAY))
    val profiles = listOf(first.copy(schedule = schedule))
    assertThat("active profile is null", NotificationProfiles.getActiveProfile(profiles, sunday930am.toMillis(ZoneOffset.UTC), utc), nullValue())
  }

  @Test
  fun `when profile is manually enabled yesterday and is scheduled also for today then return profile`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, first.id)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, sunday9am.toMillis(ZoneOffset.UTC))
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, enabled = true, start = 700, end = 900, daysEnabled = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
    val profiles = listOf(first.copy(schedule = schedule))
    assertThat("active profile is first", NotificationProfiles.getActiveProfile(profiles, monday830am.toMillis(ZoneOffset.UTC), utc), `is`(profiles[0]))
  }

  @Test
  fun `when profile is manually disabled and schedule is on but with start after end and now is before end then return null`() {
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_PROFILE, 0)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_ENABLED_UNTIL, 0)
    signalStore.dataSet.putLong(NotificationProfileValues.KEY_MANUALLY_DISABLED_AT, sunday830am.toMillis(ZoneOffset.UTC))

    val schedule = NotificationProfileSchedule(id = 3L, enabled = true, start = 2200, end = 1000, daysEnabled = DayOfWeek.values().toSet())
    val profiles = listOf(first.copy(schedule = schedule))
    assertThat("active profile is null", NotificationProfiles.getActiveProfile(profiles, sunday9am.toMillis(ZoneOffset.UTC), utc), nullValue())
  }
}
