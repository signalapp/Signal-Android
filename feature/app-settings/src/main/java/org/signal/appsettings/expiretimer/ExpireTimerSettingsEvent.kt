/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.expiretimer

import kotlin.time.Duration

/**
 * Everything that can happen on [ExpireTimerSettingsScreen].
 *
 * Events are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface ExpireTimerSettingsEvent {

  /** The user tapped the navigation icon. */
  data object NavigateBackClicked : ExpireTimerSettingsEvent

  /** The user picked one of the preset timers. */
  data class TimerSelected(val timer: Duration) : ExpireTimerSettingsEvent

  /** The user tapped the custom time row. */
  data object CustomTimeClicked : ExpireTimerSettingsEvent

  /** The user confirmed a timer in the custom time dialog. */
  data class CustomTimerSelected(val timer: Duration) : ExpireTimerSettingsEvent

  /** The custom time dialog was cancelled or dismissed. */
  data object DialogDismissed : ExpireTimerSettingsEvent

  /** The user tapped save. */
  data object SaveClicked : ExpireTimerSettingsEvent

  /** The snackbar finished showing. */
  data object SnackbarDismissed : ExpireTimerSettingsEvent
}
