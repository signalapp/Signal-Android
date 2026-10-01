/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds.custom

import android.net.Uri
import org.signal.core.util.censor
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Represents everything that can happen on the custom notifications settings screen: the user's own actions, plus the
 * screen coming back to the foreground and the recipient changing underneath us.
 */
sealed interface CustomNotificationsEvents {

  /**
   * The screen resumed, and our recipient row may no longer agree with the system notification channel.
   */
  data object Foregrounded : CustomNotificationsEvents

  /**
   * The recipient we're displaying settings for was updated.
   */
  data class RecipientChanged(val recipient: Recipient) : CustomNotificationsEvents {
    override fun toString(): String = "RecipientChanged(recipient=${recipient.id})"
  }

  /**
   * User toggled whether this recipient gets its own notification channel.
   */
  data class SetHasCustomNotifications(val enabled: Boolean) : CustomNotificationsEvents

  /**
   * User picked a new sound for messages.
   *
   * @param uri The chosen sound, or null for silence.
   */
  data class SetMessageSound(val uri: Uri?) : CustomNotificationsEvents {
    override fun toString(): String = "SetMessageSound(uri=${uri.toString().censor()})"
  }

  /**
   * User changed whether messages vibrate.
   */
  data class SetMessageVibrate(val vibrateState: VibrateState) : CustomNotificationsEvents

  /**
   * User picked a new ringtone for calls.
   *
   * @param uri The chosen ringtone, or null for silence.
   */
  data class SetCallSound(val uri: Uri?) : CustomNotificationsEvents {
    override fun toString(): String = "SetCallSound(uri=${uri.toString().censor()})"
  }

  /**
   * User changed whether calls vibrate.
   */
  data class SetCallVibrate(val vibrateState: VibrateState) : CustomNotificationsEvents

  /**
   * User tapped the message sound row and wants to pick a new one.
   */
  data object SelectMessageSound : CustomNotificationsEvents

  /**
   * User tapped the call ringtone row and wants to pick a new one.
   */
  data object SelectCallSound : CustomNotificationsEvents
}
