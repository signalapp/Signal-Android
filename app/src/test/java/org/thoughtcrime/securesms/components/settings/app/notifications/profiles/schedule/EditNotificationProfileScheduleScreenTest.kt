/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h1600dp")
class EditNotificationProfileScheduleScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<EditNotificationProfileScheduleEvent>()

  @Test
  fun `flipping the schedule toggle emits ScheduleToggled with the new value`() {
    setContent(state(enabled = true))

    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(EditNotificationProfileScheduleTestTags.ROW_SCHEDULE))).performClick()

    assertThat(events).containsExactly(EditNotificationProfileScheduleEvent.ScheduleToggled(false))
  }

  @Test
  fun `tapping a day emits DayToggled`() {
    setContent(state(enabled = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.day(DayOfWeek.WEDNESDAY)).performClick()

    assertThat(events).containsExactly(EditNotificationProfileScheduleEvent.DayToggled(DayOfWeek.WEDNESDAY))
  }

  @Test
  fun `selected days render as on`() {
    setContent(state(enabled = true, days = setOf(DayOfWeek.MONDAY)))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.day(DayOfWeek.MONDAY)).assertIsOn()
    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.day(DayOfWeek.TUESDAY)).assertIsOff()
  }

  @Test
  fun `days and times are disabled while the schedule is off`() {
    setContent(state(enabled = false))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.day(DayOfWeek.MONDAY)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.START_TIME).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `days are labeled with their full names`() {
    setContent(state(enabled = true))

    composeTestRule.onNodeWithContentDescription("Wednesday").assertExists()
  }

  @Test
  fun `tapping the start and end times emits their events`() {
    setContent(state(enabled = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.START_TIME).performClick()
    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.END_TIME).performClick()

    assertThat(events).containsExactly(EditNotificationProfileScheduleEvent.StartTimeClicked, EditNotificationProfileScheduleEvent.EndTimeClicked)
  }

  @Test
  fun `confirming the time picker emits TimeSelected`() {
    setContent(state(enabled = true, start = 2130).copy(dialog = EditNotificationProfileScheduleState.Dialog.TimePicker(isStart = true)))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_TIME_PICKER_CONFIRM).performClick()

    assertThat(events).containsExactly(EditNotificationProfileScheduleEvent.TimeSelected(LocalTime.of(21, 30)))
  }

  @Test
  fun `the button reads Skip in create mode with the schedule off`() {
    setContent(state(enabled = false, createMode = true))
    composeTestRule.onNodeWithText("Skip").assertExists()
  }

  @Test
  fun `the button reads Next in create mode with the schedule on`() {
    setContent(state(enabled = true, createMode = true))
    composeTestRule.onNodeWithText("Next").assertExists()
  }

  @Test
  fun `the button reads Save when editing`() {
    setContent(state(enabled = true))
    composeTestRule.onNodeWithText("Save").assertExists()
  }

  @Test
  fun `clicking the button emits NextClicked`() {
    setContent(state(enabled = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.BUTTON_NEXT).performClick()

    assertThat(events).containsExactly(EditNotificationProfileScheduleEvent.NextClicked)
  }

  @Test
  fun `the button is disabled until the schedule loads`() {
    setContent(EditNotificationProfileScheduleState())

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.BUTTON_NEXT).assertIsNotEnabled()
  }

  @Test
  fun `the create flow explains the schedule`() {
    setContent(state(enabled = true, createMode = true))

    composeTestRule.onNodeWithTag(EditNotificationProfileScheduleTestTags.SCROLLER).performScrollToNode(hasText("Add a schedule"))
    composeTestRule.onNodeWithText("Add a schedule").assertExists()
  }

  private fun setContent(state: EditNotificationProfileScheduleState) {
    composeTestRule.setContent {
      EditNotificationProfileScheduleScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun state(
    enabled: Boolean,
    createMode: Boolean = false,
    days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    start: Int = 900
  ): EditNotificationProfileScheduleState {
    return EditNotificationProfileScheduleState(
      createMode = createMode,
      schedule = NotificationProfileSchedule(id = 1L, enabled = enabled, start = start, end = 1700, daysEnabled = days)
    )
  }
}
