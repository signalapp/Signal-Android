package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MessageTableTest_getDaysWithMessages {

  @get:Rule
  val harness = SignalActivityRule()

  private var threadId: Long = 0

  @Before
  fun setup() {
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(harness.others.first()))
  }

  private fun insertAt(zone: ZoneId, date: LocalDate, time: LocalTime, storyType: StoryType = StoryType.NONE) {
    MmsHelper.insert(
      recipient = Recipient.resolved(harness.others.first()),
      threadId = threadId,
      sentTimeMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
      storyType = storyType
    )
  }

  private fun daysIn(zone: ZoneId): Set<LocalDate> {
    return SignalDatabase.messages.getDaysWithMessages(threadId, zone).map { LocalDate.ofEpochDay(it) }.toSet()
  }

  @Test
  fun givenNoMessages_whenIGetDays_thenIGetNothing() {
    assertThat(daysIn(ZoneId.of("UTC"))).isEmpty()
  }

  @Test
  fun givenMessagesOnThreeDays_whenIGetDays_thenIGetExactlyThoseDays() {
    val zone = ZoneId.of("UTC")
    val expected = setOf(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 15), LocalDate.of(2024, 7, 2))

    expected.forEach { insertAt(zone, it, LocalTime.of(12, 0)) }
    insertAt(zone, LocalDate.of(2024, 6, 15), LocalTime.of(23, 59))

    assertThat(daysIn(zone)).isEqualTo(expected)
  }

  @Test
  fun givenAMessageJustBeforeLocalMidnight_whenIGetDaysInANegativeOffsetZone_thenIGetTheLocalDay() {
    val zone = ZoneId.of("America/New_York")

    insertAt(zone, LocalDate.of(2024, 6, 15), LocalTime.of(23, 30))

    assertThat(daysIn(zone)).isEqualTo(setOf(LocalDate.of(2024, 6, 15)))
  }

  @Test
  fun givenAMessageJustAfterLocalMidnight_whenIGetDaysInAPositiveOffsetZone_thenIGetTheLocalDay() {
    val zone = ZoneId.of("Asia/Kathmandu")

    insertAt(zone, LocalDate.of(2024, 6, 15), LocalTime.of(0, 15))

    assertThat(daysIn(zone)).isEqualTo(setOf(LocalDate.of(2024, 6, 15)))
  }

  @Test
  fun givenMessagesSpanningADaylightSavingsTransition_whenIGetDays_thenIGetBothLocalDays() {
    val zone = ZoneId.of("America/New_York")

    insertAt(zone, LocalDate.of(2024, 3, 9), LocalTime.of(23, 30))
    insertAt(zone, LocalDate.of(2024, 3, 10), LocalTime.of(3, 30))

    assertThat(daysIn(zone)).isEqualTo(setOf(LocalDate.of(2024, 3, 9), LocalDate.of(2024, 3, 10)))
  }

  @Test
  fun givenAStory_whenIGetDays_thenItIsIgnored() {
    val zone = ZoneId.of("UTC")

    insertAt(zone, LocalDate.of(2024, 6, 1), LocalTime.of(12, 0), storyType = StoryType.STORY_WITH_REPLIES)
    insertAt(zone, LocalDate.of(2024, 6, 2), LocalTime.of(12, 0))

    assertThat(daysIn(zone)).isEqualTo(setOf(LocalDate.of(2024, 6, 2)))
  }

  @Test
  fun givenAnotherThreadsMessages_whenIGetDays_thenTheyAreIgnored() {
    val zone = ZoneId.of("UTC")
    val otherThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(harness.others[1]))

    insertAt(zone, LocalDate.of(2024, 6, 1), LocalTime.of(12, 0))
    MmsHelper.insert(
      recipient = Recipient.resolved(harness.others[1]),
      threadId = otherThreadId,
      sentTimeMillis = LocalDate.of(2024, 6, 5).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )

    assertThat(daysIn(zone)).isEqualTo(setOf(LocalDate.of(2024, 6, 1)))
  }
}
