/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

import android.net.Uri

/**
 * Everything that can happen on [NotificationsSettingsScreen].
 *
 * Events are logged. A picked sound's URI can carry the name of a file the user added, so it's left out of `toString()`.
 */
sealed interface NotificationsSettingsEvent {

  /** The screen came back into view, possibly after notification settings were changed in the system's settings. */
  data object ScreenResumed : NotificationsSettingsEvent

  /** The toolbar's navigation icon was tapped. */
  data object NavigateBackClicked : NotificationsSettingsEvent

  /** The "turn on" action of the notifications-disabled banner was tapped. */
  data object TurnOnNotificationsClicked : NotificationsSettingsEvent

  /** Message notifications were switched on or off. */
  data class MessageNotificationsToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** A notification privacy option was picked, as one of `pref_notification_privacy_values`. */
  data class MessagePrivacySelected(val value: String) : NotificationsSettingsEvent

  /** The "while muted" row was tapped. */
  data object WhileMutedClicked : NotificationsSettingsEvent

  /** Reaction notifications were switched on or off. */
  data class ReactionNotificationsToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** Unread message reminders were switched on or off. */
  data class UnreadRemindersToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** Notifications for contacts joining Signal were switched on or off. */
  data class ContactJoinsSignalToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** A repeat alerts option was picked, as one of `pref_repeat_alerts_values`. */
  data class RepeatAlertsSelected(val value: String) : NotificationsSettingsEvent

  /** The "customize" row was tapped, which is offered where the system's channel settings own sound and vibration. */
  data object CustomizeClicked : NotificationsSettingsEvent

  /** The message sound row was tapped. */
  data object MessageSoundClicked : NotificationsSettingsEvent

  /** The sound picker for message notifications returned [sound], or null when nothing was picked. */
  data class MessageSoundPicked(val sound: Uri?) : NotificationsSettingsEvent {
    override fun toString(): String = "MessageSoundPicked(hasSound=${sound != null})"
  }

  /** Vibration for message notifications was switched on or off. */
  data class MessageVibrateToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** An LED color was picked, as one of `pref_led_color_values`. */
  data class LedColorSelected(val value: String) : NotificationsSettingsEvent

  /** An LED blink pattern was picked, as one of `pref_led_blink_pattern_values`. */
  data class LedBlinkSelected(val value: String) : NotificationsSettingsEvent

  /** In-chat sounds were switched on or off. */
  data class InChatSoundsToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** The "troubleshoot" row was tapped. */
  data object TroubleshootClicked : NotificationsSettingsEvent

  /** The priority row was tapped, which is offered where the system's channel settings own priority. */
  data object PriorityClicked : NotificationsSettingsEvent

  /** A priority was picked, as one of `pref_notification_priority_values`. */
  data class PrioritySelected(val value: String) : NotificationsSettingsEvent

  /** Call notifications were switched on or off. */
  data class CallNotificationsToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** The call ringtone row was tapped. */
  data object CallRingtoneClicked : NotificationsSettingsEvent

  /** The ringtone picker returned [ringtone], or null when nothing was picked. */
  data class CallRingtonePicked(val ringtone: Uri?) : NotificationsSettingsEvent {
    override fun toString(): String = "CallRingtonePicked(hasRingtone=${ringtone != null})"
  }

  /** Vibration for calls was switched on or off. */
  data class CallVibrateToggled(val enabled: Boolean) : NotificationsSettingsEvent

  /** A sound picker couldn't be opened, because the system has nothing to handle it. */
  data object SoundPickerUnavailable : NotificationsSettingsEvent

  /** The notification profiles row was tapped. */
  data object NotificationProfilesClicked : NotificationsSettingsEvent

  /** The reset row was tapped. */
  data object ResetClicked : NotificationsSettingsEvent

  /** The reset was confirmed in its dialog. */
  data object ResetConfirmed : NotificationsSettingsEvent

  /** The dialog was dismissed without confirming. */
  data object DialogDismissed : NotificationsSettingsEvent

  /** The snackbar finished showing. */
  data object SnackbarDismissed : NotificationsSettingsEvent
}
