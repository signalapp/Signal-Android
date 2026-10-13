/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.sounds

import org.thoughtcrime.securesms.database.RecipientTable.NotificationSetting

/**
 * Represents all user-driven actions that can occur on the Sounds & Notifications settings screen.
 */
sealed interface SoundsAndNotificationsEvent {

  /**
   * Mutes notifications for this recipient until the given epoch-millisecond timestamp.
   *
   * @param muteUntil Epoch-millisecond timestamp after which notifications should resume.
   *                  Use [Long.MAX_VALUE] to mute indefinitely.
   */
  data class SetMuteUntil(val muteUntil: Long) : SoundsAndNotificationsEvent

  /**
   * Clears any active mute, immediately restoring notifications for this recipient.
   */
  data object Unmute : SoundsAndNotificationsEvent

  /**
   * Signals that the user tapped the "Custom Notifications" row and wishes to navigate to the
   * [custom notifications settings screen][org.thoughtcrime.securesms.components.settings.conversation.sounds.custom.CustomNotificationsSettingsFragment].
   */
  data object NavigateToCustomNotifications : SoundsAndNotificationsEvent

  /**
   * User tapped "When Muted" and navigates to [MutedNotificationsFragment]
   */
  data object NavigateToMutedNotifications : SoundsAndNotificationsEvent

  /**
   * User toggled "Unread reminders"
   */
  data class SetUnreadReminder(val setting: NotificationSetting) : SoundsAndNotificationsEvent
}
