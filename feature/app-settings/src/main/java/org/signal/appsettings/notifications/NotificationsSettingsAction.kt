/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the screen
 * itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface NotificationsSettingsAction {

  /** Explain how to turn notifications back on in the system's settings. */
  data object ShowTurnOnNotificationsSheet : NotificationsSettingsAction

  /** Open the system's settings for the message notification channel. */
  data object OpenMessageChannelSettings : NotificationsSettingsAction

  /** Open a picker for the message notification sound. */
  data object LaunchMessageSoundPicker : NotificationsSettingsAction

  /** Open a picker for the call ringtone. */
  data object LaunchCallRingtonePicker : NotificationsSettingsAction

  /** Open the system's settings for the message notification channel's priority. */
  data object LaunchNotificationPrioritySettings : NotificationsSettingsAction

  /** Walk the user through what's delaying their notifications. */
  data object ShowTroubleshootNotificationsDialog : NotificationsSettingsAction
}
