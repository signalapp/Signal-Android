/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notifications

import android.os.Build

/**
 * What [NotificationsSettingsScreen] renders.
 *
 * @param notifyWhenContactJoinsSignal whether to notify when a contact joins Signal.
 */
data class NotificationsSettingsState(
  val messageNotificationsState: MessageNotificationsState = MessageNotificationsState(),
  val callNotificationsState: CallNotificationsState = CallNotificationsState(),
  val notifyWhenContactJoinsSignal: Boolean = false,
  val deviceState: DeviceState = DeviceState(),
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Asks before resetting every notification setting, including each chat's. */
    data object ConfirmReset : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** The system has no app to pick a sound with. */
    data object SoundPickerUnavailable : Snackbar
  }
}

/**
 * Message notification settings.
 *
 * @param canEnableNotifications false when the system has notifications turned off for Signal, so they can't be turned on here.
 * @param troubleshootNotifications whether notifications look delayed enough to offer troubleshooting.
 */
data class MessageNotificationsState(
  val notificationsEnabled: Boolean = false,
  val canEnableNotifications: Boolean = true,
  val sound: SoundSummary = SoundSummary.Default,
  val vibrateEnabled: Boolean = false,
  val ledColor: String = "",
  val ledBlink: String = "",
  val inChatSoundsEnabled: Boolean = false,
  val repeatAlerts: Int = 0,
  val messagePrivacy: String = "",
  val priority: Int = 0,
  val troubleshootNotifications: Boolean = false,
  val reactionNotificationEnabled: Boolean = false,
  val unreadReminderEnabled: Boolean = false,
  val allowCallsWhileMuted: Boolean = false,
  val allowMentionsWhileMuted: Boolean = false,
  val allowRepliesWhileMuted: Boolean = false
)

/**
 * Call notification settings.
 *
 * @param canEnableNotifications false when the system has notifications turned off for Signal, so they can't be turned on here.
 */
data class CallNotificationsState(
  val notificationsEnabled: Boolean = false,
  val canEnableNotifications: Boolean = true,
  val ringtone: SoundSummary = SoundSummary.Default,
  val vibrateEnabled: Boolean = false
)

/**
 * How a chosen notification sound or ringtone is described.
 */
sealed interface SoundSummary {
  /** The system default sound. */
  data object Default : SoundSummary

  /** No sound. */
  data object Silent : SoundSummary

  /** A sound whose name can't be read. */
  data object Unknown : SoundSummary

  /** A sound with the given name. */
  data class Named(val title: String) : SoundSummary
}

/**
 * What the device supports, which decides which sound settings are offered here rather than in the system's settings.
 */
data class DeviceState(
  val apiLevel: Int = Build.VERSION.SDK_INT,
  val supportsNotificationChannels: Boolean = Build.VERSION.SDK_INT >= 26
)
