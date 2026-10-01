/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import org.signal.mediakeyboard.MediaKeyboardState
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji

sealed interface EmojiPageScreenEvents {
  data object Initialize : EmojiPageScreenEvents
  data class ParentStateChanged(val parentState: MediaKeyboardState) : EmojiPageScreenEvents
  data class CategorySelected(val category: EmojiKeyboardCategory) : EmojiPageScreenEvents
  data class VisibleCategoryChanged(val category: EmojiKeyboardCategory) : EmojiPageScreenEvents
  data object ScrollTargetConsumed : EmojiPageScreenEvents
  data class EmojiClicked(val emoji: KeyboardEmoji) : EmojiPageScreenEvents
  data class EmojiLongPressed(val cellKey: String, val emoji: KeyboardEmoji) : EmojiPageScreenEvents
  data class VariationSelected(val emoji: KeyboardEmoji, val variation: String) : EmojiPageScreenEvents
  data object VariationSelectorDismissed : EmojiPageScreenEvents
}
