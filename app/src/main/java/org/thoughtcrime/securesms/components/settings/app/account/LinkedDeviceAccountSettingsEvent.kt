/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

sealed interface LinkedDeviceAccountSettingsEvent {
  /** The user tapped "Learn more" on the linked-device callout. */
  data object LearnMoreClicked : LinkedDeviceAccountSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : LinkedDeviceAccountSettingsEvent

  /** The user tapped the "Delete app data" row. */
  data object DeleteAppDataClicked : LinkedDeviceAccountSettingsEvent

  /** The user confirmed the delete in the confirmation dialog. */
  data object DeleteConfirmed : LinkedDeviceAccountSettingsEvent

  /** The user dismissed the delete confirmation dialog. */
  data object DeleteDismissed : LinkedDeviceAccountSettingsEvent

  /** The fragment reported that clearing application data failed. */
  data object DataWipeFailed : LinkedDeviceAccountSettingsEvent

  /** The fragment has handled the current [LinkedDeviceAccountSettingsState.oneTimeEvent]. */
  data object ConsumeOneTimeEvent : LinkedDeviceAccountSettingsEvent
}
