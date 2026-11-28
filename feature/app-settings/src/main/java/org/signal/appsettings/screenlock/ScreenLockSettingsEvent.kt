/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import kotlin.time.Duration

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface ScreenLockSettingsEvent {

  /** The screen came back to the foreground, so whether the device can authenticate may have changed. */
  data object ScreenResumed : ScreenLockSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : ScreenLockSettingsEvent

  /** The user authenticated to turn screen lock on or off. Only sent once authentication succeeds. */
  data class ScreenLockToggled(val enabled: Boolean) : ScreenLockSettingsEvent

  /** The user picked one of [ScreenLockSettingsState.PRESET_TIMEOUTS]. */
  data class TimeoutSelected(val timeout: Duration) : ScreenLockSettingsEvent

  /** The user tapped the custom time option, which asks them to pick a timeout. */
  data object CustomTimeoutClicked : ScreenLockSettingsEvent

  /** The user picked a custom timeout. */
  data class CustomTimeoutSelected(val timeout: Duration) : ScreenLockSettingsEvent

  /** Dismisses whatever is in [ScreenLockSettingsState.dialog]. */
  data object DialogDismissed : ScreenLockSettingsEvent
}
