package org.thoughtcrime.securesms.notifications.v2

import org.signal.core.util.logging.Log

/**
 * Suppressor for notification sounds.
 */
object InChatNotificationSoundSuppressor {
  private val TAG = Log.tag(InChatNotificationSoundSuppressor::class.java)
  var isSuppressed: Boolean = false

  @JvmStatic
  fun suppressNotification() {
    isSuppressed = true
    Log.d(TAG, "Notification is suppressed.")
  }

  @JvmStatic
  fun allowNotification() {
    isSuppressed = false
    Log.d(TAG, "Notification is allowed.")
  }
}
