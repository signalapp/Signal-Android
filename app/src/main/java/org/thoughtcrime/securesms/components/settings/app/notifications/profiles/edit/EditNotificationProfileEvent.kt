/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface EditNotificationProfileEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : EditNotificationProfileEvent

  /** The user edited the name. */
  data class NameChanged(val name: String) : EditNotificationProfileEvent {
    override fun toString(): String = "NameChanged(length=${name.length})"
  }

  /** The user tapped the emoji button beside the name. */
  data object EmojiButtonClicked : EditNotificationProfileEvent

  /** The user picked [emoji] from the emoji picker. */
  data class EmojiSelected(val emoji: String) : EditNotificationProfileEvent

  /** The emoji picker was dismissed without a pick. */
  data object EmojiPickerDismissed : EditNotificationProfileEvent

  /** The user tapped the clear button beside the name. */
  data object ClearClicked : EditNotificationProfileEvent

  /** The user tapped one of the suggested profiles, named [name] in their language. */
  data class PresetClicked(val name: String, val emoji: String) : EditNotificationProfileEvent {
    override fun toString(): String = "PresetClicked(emoji=$emoji)"
  }

  /** The user tapped save, or the keyboard's done key. */
  data object SaveClicked : EditNotificationProfileEvent
}
