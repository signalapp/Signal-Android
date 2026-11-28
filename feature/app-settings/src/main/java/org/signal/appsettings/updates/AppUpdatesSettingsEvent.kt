/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AppUpdatesSettingsEvent {

  /** The screen came back to the foreground, so the last check time may be stale. */
  data object ScreenResumed : AppUpdatesSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AppUpdatesSettingsEvent

  /** The user flipped the automatic updates toggle. */
  data class AutoUpdateToggled(val enabled: Boolean) : AppUpdatesSettingsEvent

  /** The user tapped the row to check for updates. */
  data object CheckForUpdatesClicked : AppUpdatesSettingsEvent
}
