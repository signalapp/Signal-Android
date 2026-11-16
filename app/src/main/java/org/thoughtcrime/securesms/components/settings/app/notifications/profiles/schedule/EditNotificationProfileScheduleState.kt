/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule

/**
 * [EditNotificationProfileScheduleScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class EditNotificationProfileScheduleState(
  /** Whether this is a step in creating a new profile, rather than editing an existing one's schedule. */
  val createMode: Boolean = false,
  /** The schedule being edited, or null until it has been read. */
  val schedule: NotificationProfileSchedule? = null,
  /** Whether the schedule is being saved. */
  val isSaving: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Whether the next/save button can be used. */
  val canSave: Boolean
    get() = schedule != null && !isSaving

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Picks the schedule's start time if [isStart], otherwise its end time. */
    data class TimePicker(val isStart: Boolean) : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** The schedule is on but has no days, so it can't be saved. */
    data object NoDaysSelected : Snackbar
  }
}
