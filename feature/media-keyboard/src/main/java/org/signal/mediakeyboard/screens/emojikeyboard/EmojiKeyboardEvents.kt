/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojikeyboard

import org.signal.mediakeyboard.screens.emoji.EmojiPageScreenEvents

sealed interface EmojiKeyboardEvents {
  /** Something happened in the emoji grid, its category rail or the search results. */
  data class EmojiPageScreenEvent(val event: EmojiPageScreenEvents) : EmojiKeyboardEvents

  /** The user tapped backspace. */
  data object BackspaceClicked : EmojiKeyboardEvents

  /** The user tapped search, either the icon or the field above the grid. */
  data object SearchClicked : EmojiKeyboardEvents

  /** The user left search, by its back arrow or the back gesture. */
  data object SearchCloseClicked : EmojiKeyboardEvents
}
