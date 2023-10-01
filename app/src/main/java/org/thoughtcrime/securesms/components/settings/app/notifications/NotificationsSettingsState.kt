package org.thoughtcrime.securesms.components.settings.app.notifications

import android.net.Uri

data class NotificationsSettingsState(
  val messageNotificationsState: MessageNotificationsState,
  val callNotificationsState: CallNotificationsState,
  val notifyWhenContactJoinsSignal: Boolean
)

data class MessageNotificationsState(
  val notificationsEnabled: Boolean,
  val canEnableNotifications: Boolean,
  val sound: Uri,
  val vibrateEnabled: Boolean,
  val ledColor: String,
  val ledBlink: String,
  val inChatSoundsEnabled: Boolean,
  val repeatAlerts: Int,
  val messagePrivacy: String,
  val priority: Int,
  val troubleshootNotifications: Boolean
)

data class CallNotificationsState(
  val notificationsEnabled: Boolean,
  val canEnableNotifications: Boolean,
  val ringtone: Uri,
  val vibrateEnabled: Boolean
)
