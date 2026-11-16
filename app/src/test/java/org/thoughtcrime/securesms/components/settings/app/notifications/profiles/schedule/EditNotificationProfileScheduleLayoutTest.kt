/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import java.time.DayOfWeek

class EditNotificationProfileScheduleLayoutTest : SignalLayoutStressTest() {

  private val schedule = NotificationProfileSchedule(
    id = 1L,
    enabled = true,
    start = 2145,
    end = 1215,
    daysEnabled = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
  )

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    EditNotificationProfileScheduleScreen(state = EditNotificationProfileScheduleState(createMode = true), onEvent = {})
  }

  @Test
  fun `create mode`() = assertPassesLayoutChecks {
    EditNotificationProfileScheduleScreen(state = EditNotificationProfileScheduleState(createMode = true, schedule = schedule), onEvent = {})
  }

  @Test
  fun `editing with the schedule off`() = assertPassesLayoutChecks {
    EditNotificationProfileScheduleScreen(state = EditNotificationProfileScheduleState(schedule = schedule.copy(enabled = false)), onEvent = {})
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    EditNotificationProfileScheduleScreen(state = EditNotificationProfileScheduleState(schedule = schedule, isSaving = true), onEvent = {})
  }

  /** Material's clock face lays its hour numbers' touch targets over each other by design, so only the dialog around it is checked. */
  @Test
  fun `picking a time`() = assertPassesLayoutChecks(ignoreTags = setOf(Dialogs.TEST_TAG_TIME_PICKER_DIAL)) {
    EditNotificationProfileScheduleScreen(
      state = EditNotificationProfileScheduleState(schedule = schedule, dialog = EditNotificationProfileScheduleState.Dialog.TimePicker(isStart = true)),
      onEvent = {}
    )
  }
}
