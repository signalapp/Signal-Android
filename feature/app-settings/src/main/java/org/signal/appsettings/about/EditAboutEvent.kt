/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.about

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface EditAboutEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : EditAboutEvent

  /** The user edited the about text. */
  data class AboutChanged(val about: String) : EditAboutEvent {
    override fun toString(): String = "AboutChanged(length=${about.length})"
  }

  /** The user tapped the emoji button beside the about text. */
  data object EmojiButtonClicked : EditAboutEvent

  /** The user picked [emoji] from the emoji picker. */
  data class EmojiSelected(val emoji: String) : EditAboutEvent

  /** The emoji picker was dismissed without a pick. */
  data object EmojiPickerDismissed : EditAboutEvent

  /** The user tapped a preset, which replaces both the emoji and the about text. */
  data class PresetClicked(val emoji: String, val about: String) : EditAboutEvent {
    override fun toString(): String = "PresetClicked(emoji=$emoji)"
  }

  /** The user tapped the clear button, which empties both the emoji and the about text. */
  data object ClearClicked : EditAboutEvent

  /** The user tapped save, or the keyboard's done action. */
  data object SaveClicked : EditAboutEvent

  /** Whatever was in [EditAboutState.snackbar] has gone. */
  data object SnackbarDismissed : EditAboutEvent
}
