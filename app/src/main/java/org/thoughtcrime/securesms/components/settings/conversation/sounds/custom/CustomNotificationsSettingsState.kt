package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

data class CustomNotificationsSettingsState(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val isInitialLoadComplete: Boolean = false,
  val supportsNotificationChannels: Boolean = false,
  val canOpenChannelSettings: Boolean = false,
  val notificationChannel: String? = null,
  val messageVibrateState: VibrateState = VibrateState.DEFAULT,
  val messageVibrateEnabled: Boolean = false,
  val messageSound: Uri? = null,
  val callVibrateState: VibrateState = VibrateState.DEFAULT,
  val callSound: Uri? = null,
  val showCallingOptions: Boolean = false
) {
  val hasCustomNotifications: Boolean = supportsNotificationChannels && notificationChannel != null

  val controlsEnabled: Boolean = isInitialLoadComplete && (!supportsNotificationChannels || hasCustomNotifications)
}
