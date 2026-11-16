/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emojisheet

import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.emoji.previewEmojiPageState

/**
 * An [EmojiSheetState] with a "This message" section ahead of [previewEmojiPageState]'s, for previews and tests.
 */
fun previewEmojiSheetState(showCustomizeReactions: Boolean = true): EmojiSheetState {
  val page = previewEmojiPageState()
  val thisMessage = EmojiCategoryPage(EmojiKeyboardCategory.THIS_MESSAGE, listOf("👍", "❤️").map { KeyboardEmoji(it) })

  return EmojiSheetState(
    page = page.copy(pages = listOf(thisMessage) + page.pages, selectedCategory = EmojiKeyboardCategory.THIS_MESSAGE),
    showCustomizeReactions = showCustomizeReactions
  )
}
