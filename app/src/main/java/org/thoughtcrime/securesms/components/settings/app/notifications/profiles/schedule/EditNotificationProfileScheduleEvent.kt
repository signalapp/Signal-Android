/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface EditNotificationProfileScheduleEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : EditNotificationProfileScheduleEvent

  /** The user flipped the schedule toggle. */
  data class ScheduleToggled(val enabled: Boolean) : EditNotificationProfileScheduleEvent

  /** The user tapped one of the day-of-week toggles. */
  data class DayToggled(val day: DayOfWeek) : EditNotificationProfileScheduleEvent

  /** The user tapped the start time. */
  data object StartTimeClicked : EditNotificationProfileScheduleEvent

  /** The user tapped the end time. */
  data object EndTimeClicked : EditNotificationProfileScheduleEvent

  /** The user confirmed a time in the time picker. */
  data class TimeSelected(val time: LocalTime) : EditNotificationProfileScheduleEvent

  /** The user cancelled or dismissed the time picker. */
  data object DialogDismissed : EditNotificationProfileScheduleEvent

  /** The user tapped the next, skip, or save button. */
  data object NextClicked : EditNotificationProfileScheduleEvent

  /** The snackbar in [EditNotificationProfileScheduleState.snackbar] went away. */
  data object SnackbarDismissed : EditNotificationProfileScheduleEvent
}
