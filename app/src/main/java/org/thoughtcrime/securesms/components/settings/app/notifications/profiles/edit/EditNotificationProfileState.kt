/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import org.signal.core.util.StringUtil

/**
 * [EditNotificationProfileScreen] state.
 */
data class EditNotificationProfileState(
  /** True when creating a new profile rather than editing an existing one. */
  val createMode: Boolean = true,
  /** False until an existing profile's name and emoji have been read. */
  val isLoaded: Boolean = false,
  /** The profile's name, kept within [NAME_MAX_GRAPHEMES]. */
  val name: String = "",
  /** The profile's emoji, or empty for none. */
  val emoji: String = "",
  /** Whether the emoji picker is open. */
  val showEmojiPicker: Boolean = false,
  val isSaving: Boolean = false,
  val nameError: NameError = NameError.None
) {

  companion object {
    const val NAME_MAX_GRAPHEMES = 32
    const val NAME_COUNT_DISPLAY_THRESHOLD = 22
  }

  /** How long [name] is, counting an emoji or accented letter as one. */
  val nameLength: Int
    get() = StringUtil.getGraphemeCount(name)

  override fun toString(): String {
    return "EditNotificationProfileState(createMode=$createMode, isLoaded=$isLoaded, nameLength=${name.length}, hasEmoji=${emoji.isNotEmpty()}, showEmojiPicker=$showEmojiPicker, isSaving=$isSaving, nameError=$nameError)"
  }

  /** Why the name can't be saved, if anything. */
  enum class NameError {
    None,
    Empty,
    Duplicate
  }
}
