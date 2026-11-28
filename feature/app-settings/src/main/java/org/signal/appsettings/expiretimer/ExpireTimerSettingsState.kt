/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.expiretimer

import androidx.annotation.StringRes
import kotlin.time.Duration

/**
 * [ExpireTimerSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class ExpireTimerSettingsState(
  /** What the timer is being chosen for. */
  val mode: Mode = Mode.UNIVERSAL,
  /** The timer in effect when the screen opened, or the chat's current timer as it changes. */
  val initialTimer: Duration = Duration.ZERO,
  /** The timer the user picked, if they've picked one. */
  val selectedTimer: Duration? = null,
  /** A save is in flight, so the save button shows progress and can't be pressed again. */
  val isSaving: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** The timer the screen shows as selected. */
  val currentTimer: Duration
    get() = selectedTimer ?: initialTimer

  enum class Mode {
    /** The default timer for new chats the user starts. */
    UNIVERSAL,

    /** The timer for one existing chat, saved to that chat. */
    RECIPIENT,

    /** The timer for a group being created, handed back to the create flow rather than saved. */
    GROUP_CREATE
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Picking a timer that isn't one of the presets. */
    data object CustomTimer : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** Saving the chat's timer failed, for the reason in [message], a string resource supplied by the host. */
    data class SaveFailed(@param:StringRes val message: Int) : Snackbar
  }
}
