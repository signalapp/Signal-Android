package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.recipients.Recipient

data class CustomNotificationsSettingsState(
  val isInitialLoadComplete: Boolean = false,
  val recipient: Recipient? = null,
  val hasCustomNotifications: Boolean = false,
  val controlsEnabled: Boolean = false,
  val messageVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
  val messageVibrateEnabled: Boolean = false,
  val messageSound: Uri? = null,
  val callVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
  val callSound: Uri? = null,
  val showCallingOptions: Boolean = false,
)
